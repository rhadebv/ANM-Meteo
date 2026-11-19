package ro.anmmeteo.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.*

private val Navy=Color(0xFF071824);private val Navy2=Color(0xFF123C56);private val Blue=Color(0xFF2196F3);private val Soft=Color(0xFFB6C7D4)
data class Station(val name:String,val temp:String,val wind:String,val humidity:String,val pressure:String,val condition:String,val date:String)
data class Forecast(val day:String,val condition:String,val min:String,val max:String)
data class Warning(val level:String,val title:String,val body:String,val source:String)
data class State(val loading:Boolean=true,val stations:List<Station> = emptyList(),val selected:String="BRASOV GHIMBAV",val forecast:List<Forecast> = emptyList(),val warnings:List<Warning> = emptyList(),val warningError:String?=null,val warningChecked:String="",val gpsText:String="",val error:String?=null)
data class Point(val name:String,val lat:Double,val lon:Double)

private val verifiedStations=listOf(
 Point("BRASOV GHIMBAV",45.696,25.526),Point("CALARASI",44.205,27.313),Point("BUCURESTI BANEASA",44.510,26.078),Point("CLUJ-NAPOCA",46.777,23.571),Point("SIBIU",45.789,24.091),Point("FAGARAS",45.842,24.973),Point("FUNDATA",45.443,25.303),Point("PREDEAL",45.505,25.579),Point("SFANTU GHEORGHE",45.866,25.787),Point("TARGU MURES",46.533,24.533),Point("TIMISOARA",45.772,21.259),Point("IASI",47.178,27.620),Point("CONSTANTA",44.180,28.650),Point("CRAIOVA",44.318,23.889),Point("ORADEA",47.035,21.897),Point("ARAD",46.176,21.319),Point("BACAU",46.522,26.910),Point("SUCEAVA",47.687,26.354),Point("GALATI",45.437,28.024),Point("PLOIESTI",44.946,26.013)
)

class VM(app:Application):AndroidViewModel(app){
 var s by mutableStateOf(State());private set
 private val p=app.getSharedPreferences("cache",Context.MODE_PRIVATE)
 private val http=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS).build()
 init{s=s.copy(selected=p.getString("selected","BRASOV GHIMBAV")?:"BRASOV GHIMBAV");refresh(false)}
 fun select(n:String){s=s.copy(selected=n);p.edit().putString("selected",n).apply();loadForecast();widget()}
 fun gps(l:Location){val q=verifiedStations.minByOrNull{dist(l.latitude,l.longitude,it.lat,it.lon)}?:return;val km=dist(l.latitude,l.longitude,q.lat,q.lon);if(km>250){s=s.copy(gpsText="Poziția GPS nu pare validă pentru România (${km.roundToInt()} km până la o stație cunoscută).");return};select(q.name);s=s.copy(gpsText="${q.name} • ${"%.1f".format(km)} km • precizie GPS ${l.accuracy.roundToInt()} m")}
 fun gpsStatus(t:String){s=s.copy(gpsText=t)}
 fun refresh(force:Boolean=true){viewModelScope.launch{s=s.copy(loading=true,error=null);runCatching{cached("stations",30,force){get("https://www.meteoromania.ro/wp-json/meteoapi/v2/starea-vremii")}}.mapCatching{stations(it)}.onSuccess{s=s.copy(loading=false,stations=it);widget()}.onFailure{s=s.copy(loading=false,error=it.message)};loadForecast();loadWarnings(force)}}
 private fun loadForecast(){viewModelScope.launch{runCatching{cached("forecast",30,false){get("https://www.meteoromania.ro/wp-json/meteoapi/v2/prognoza-orase")}}.mapCatching{forecast(it,s.selected)}.onSuccess{s=s.copy(forecast=it)}}}
 private fun loadWarnings(force:Boolean){viewModelScope.launch{val checked=SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date());runCatching{val general=cached("warn_general",15,force){get("https://www.meteoromania.ro/wp-json/meteoapi/v2/avertizari-generale")};val now=cached("warn_now",15,force){get("https://www.meteoromania.ro/wp-json/meteoapi/v2/avertizari-nowcasting")};warnings(general,"Avertizare generală")+warnings(now,"Nowcasting")}.onSuccess{s=s.copy(warnings=it,warningError=null,warningChecked=checked)}.onFailure{s=s.copy(warningError="Conexiunea la fluxurile ANM a eșuat: ${it.message}",warningChecked=checked)}}}
 private suspend fun cached(k:String,mins:Int,force:Boolean,fetch:()->String)=withContext(Dispatchers.IO){val d=p.getString(k,null);val t=p.getLong("${k}_t",0);if(!force&&d!=null&&System.currentTimeMillis()-t<mins*60000L)d else try{fetch().also{p.edit().putString(k,it).putLong("${k}_t",System.currentTimeMillis()).apply()}}catch(e:Exception){d?:throw e}}
 private fun get(u:String)=http.newCall(Request.Builder().url(u).header("User-Agent","Mozilla/5.0 ANMMeteo/0.4").build()).execute().use{if(!it.isSuccessful)error("HTTP ${it.code}");it.body?.string()?:error("răspuns gol")}
 private fun stations(raw:String):List<Station>{val a=if(raw.trim().startsWith("["))JSONArray(raw)else JSONObject(raw).optJSONArray("features")?:JSONObject(raw).optJSONArray("data")?:JSONArray();val out=mutableListOf<Station>();for(i in 0 until a.length()){val b=a.optJSONObject(i)?:continue;val o=b.optJSONObject("properties")?:b;fun f(vararg k:String):String{k.forEach{val v=o.opt(it);if(v!=null&&v.toString()!="null"&&v.toString().isNotBlank())return v.toString()};return"—"};val n=f("nume","name","statie","station","denumire").uppercase();if(n!="—")out+=Station(n,f("temperatura","temp","temperature"),f("vant","wind","viteza_vant"),f("umezeala","umiditate","humidity"),f("presiune","pressure"),f("nebulozitate","cloud","stare_vreme","fenomen"),f("data","date","timestamp","ora"))};return out.sortedBy{it.name}}
 private fun forecast(raw:String,station:String):List<Forecast>{val all=mutableListOf<JSONObject>();fun walk(v:Any?){when(v){is JSONObject->{all+=v;v.keys().forEach{walk(v.opt(it))}};is JSONArray->for(i in 0 until v.length())walk(v.opt(i))}};walk(if(raw.trim().startsWith("["))JSONArray(raw)else JSONObject(raw));fun f(o:JSONObject,vararg k:String):String{k.forEach{val v=o.opt(it);if(v!=null&&v !is JSONObject&&v !is JSONArray&&v.toString()!="null"&&v.toString().isNotBlank())return v.toString()};return""};val key=station.substringBefore(" ").lowercase();val filtered=all.filter{val city=f(it,"oras","localitate","nume","name","city").lowercase();city.contains(key)||key.contains(city)||city.isBlank()};val rows=filtered.mapNotNull{o->val min=f(o,"tmin","temp_min","min","temperatura_minima");val max=f(o,"tmax","temp_max","max","temperatura_maxima");if(min.isBlank()&&max.isBlank())null else Forecast(f(o,"data","date","zi","day","valabilitate"),f(o,"fenomen","descriere","description","stare","weather").ifBlank{"Date ANM"},min,max)}.distinctBy{"${it.min}|${it.max}|${it.condition}"}.take(7);return rows.mapIndexed{i,x->x.copy(day=dayLabel(x.day,i))}}
 private fun dayLabel(raw:String,index:Int):String{if(raw.isNotBlank()&&raw.lowercase()!="prognoză")return raw;val d=LocalDate.now().plusDays(index.toLong());return when(index){0->"Astăzi";1->"Mâine";else->d.format(DateTimeFormatter.ofPattern("EEEE, d MMM",Locale("ro","RO"))).replaceFirstChar{it.uppercase()}}}
 private fun warnings(raw:String,source:String):List<Warning>{val all=mutableListOf<JSONObject>();fun walk(v:Any?){when(v){is JSONObject->{all+=v;v.keys().forEach{walk(v.opt(it))}};is JSONArray->for(i in 0 until v.length())walk(v.opt(i))}};runCatching{walk(if(raw.trim().startsWith("["))JSONArray(raw)else JSONObject(raw))};fun f(o:JSONObject,vararg k:String):String{k.forEach{val v=o.opt(it);if(v!=null&&v !is JSONObject&&v !is JSONArray&&v.toString()!="null"&&v.toString().isNotBlank())return v.toString()};return""};return all.mapNotNull{o->val title=f(o,"titlu","title","headline","fenomen","mesaj");val body=f(o,"descriere","description","text","zone","continut");if(title.isBlank()&&body.isBlank())null else{val u=(title+body).uppercase();Warning(when{u.contains("ROȘU")||u.contains("ROSU")->"COD ROȘU";u.contains("PORTOCALIU")->"COD PORTOCALIU";u.contains("GALBEN")->"COD GALBEN";else->"INFORMARE"},title.ifBlank{source},body,source)}}.distinctBy{it.title+it.body}.take(30)}
 private fun widget(){val c=s.stations.firstOrNull{it.name==s.selected}?:return;val fc=s.forecast.firstOrNull();p.edit().putString("location",c.name.replace(" GHIMBAV"," ")).putString("temp",temp(c.temp)).putString("condition",c.condition).putString("updated","Actualizat ${SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date())}").putString("details",fc?.let{"${it.day}: ${temp(it.min)} / ${temp(it.max)}"}?:"Umiditate ${c.humidity}%").apply();WeatherWidgetProvider.refreshAll(getApplication())}
 private fun dist(a:Double,b:Double,c:Double,d:Double):Double{val r=6371.0;val x=sin(Math.toRadians(c-a)/2).pow(2)+cos(Math.toRadians(a))*cos(Math.toRadians(c))*sin(Math.toRadians(d-b)/2).pow(2);return 2*r*atan2(sqrt(x),sqrt(1-x))}
}
fun temp(v:String)=Regex("-?\\d+(?:[.,]\\d+)?").find(v)?.value?.let{"${it.replace(',','.')}°C"}?:v

class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContent{App()}}}
enum class Tab{HOME,STATIONS,RADAR,ALERTS,FAVORITES}
@Composable fun App(vm:VM= viewModel()){var tab by remember{mutableStateOf(Tab.HOME)};MaterialTheme(colorScheme=darkColorScheme(primary=Blue,background=Navy,surface=Navy2)){Scaffold(containerColor=Navy,bottomBar={NavigationBar(containerColor=Navy){nav(Tab.HOME,tab,"Acasă",Icons.Default.Home){tab=it};nav(Tab.STATIONS,tab,"Stații",Icons.Default.Place){tab=it};nav(Tab.RADAR,tab,"Radar",Icons.Default.Map){tab=it};nav(Tab.ALERTS,tab,"Alerte",Icons.Default.Warning){tab=it};nav(Tab.FAVORITES,tab,"Favorite",Icons.Default.Star){tab=it}}}){pad->Box(Modifier.padding(pad).fillMaxSize().background(Brush.verticalGradient(listOf(Navy2,Navy)))){when(tab){Tab.HOME->Home(vm);Tab.STATIONS->Stations(vm);Tab.RADAR->Radar();Tab.ALERTS->Alerts(vm);Tab.FAVORITES->Favorites(vm)}}}}}
@Composable private fun RowScope.nav(t:Tab,c:Tab,l:String,i:ImageVector,on:(Tab)->Unit){NavigationBarItem(t==c,{on(t)},{Icon(i,l)},label={Text(l,fontSize=10.sp)})}
@Composable fun Header(a:String,b:String){Column(Modifier.fillMaxWidth().padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(a,24.sp,FontWeight.Bold,color=Color.White);Text(b,color=Soft)}}
@Composable fun Home(vm:VM){val st=vm.s;val cur=st.stations.firstOrNull{it.name==st.selected}?:st.stations.firstOrNull();val ctx=LocalContext.current;val req=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){if(it.values.any{v->v})freshGps(ctx,vm)};LazyColumn{item{Header("ANM Meteo","România, mereu la zi")};item{Row(Modifier.padding(16.dp).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({if(ContextCompat.checkSelfPermission(ctx,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)freshGps(ctx,vm)else req.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))},Modifier.weight(1f)){Icon(Icons.Default.MyLocation,null);Text(" Locația mea")};OutlinedButton({vm.refresh(true)}){Icon(Icons.Default.Refresh,null)}};if(st.gpsText.isNotBlank())Text(st.gpsText,Modifier.padding(horizontal=18.dp),color=Soft)};item{if(cur!=null)Current(cur)else Text(st.error?:"Se încarcă",Modifier.padding(24.dp),color=Color.White)};item{ForecastRow(st.forecast)};item{Text("Stații meteo",Modifier.padding(18.dp),fontSize=20.sp,fontWeight=FontWeight.Bold,color=Color.White)};items(st.stations.take(6)){StationRow(it,vm)}}}
@SuppressLint("MissingPermission") fun freshGps(c:Context,vm:VM){val m=c.getSystemService(Context.LOCATION_SERVICE)as LocationManager;vm.gpsStatus("Se caută poziția GPS…");val l=object:LocationListener{override fun onLocationChanged(x:Location){if(x.time>System.currentTimeMillis()-120000&&(!x.hasAccuracy()||x.accuracy<=500)){vm.gps(x);m.removeUpdates(this)}};override fun onProviderDisabled(p:String){vm.gpsStatus("Activează localizarea telefonului.")}};listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER).filter{m.isProviderEnabled(it)}.forEach{m.requestLocationUpdates(it,0,0f,l,Looper.getMainLooper())}}
@Composable fun WeatherIcon(c:String,size:Int=72){Icon(painter=androidx.compose.ui.res.painterResource(WeatherIcons.resource(c)),contentDescription=c,tint=Color.Unspecified,modifier=Modifier.size(size.dp))}
@Composable fun Current(x:Station){Card(Modifier.padding(16.dp).fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Navy2),shape=RoundedCornerShape(26.dp)){Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(x.name.replace(" GHIMBAV",""),24.sp,FontWeight.Bold,color=Color.White);WeatherIcon(x.condition,90);Text(temp(x.temp),58.sp,color=Color.White);Text(x.condition,18.sp,color=Color.White);Row(Modifier.fillMaxWidth().padding(top=20.dp),horizontalArrangement=Arrangement.SpaceAround){metric("Umiditate",x.humidity+"%");metric("Vânt",x.wind);metric("Presiune",x.pressure)}}}}
@Composable fun metric(a:String,b:String){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(a,color=Soft,fontSize=12.sp);Text(b,color=Color.White,fontWeight=FontWeight.Bold)}}
@Composable fun ForecastRow(f:List<Forecast>){Column{Text("Prognoză pe zile",Modifier.padding(18.dp),20.sp,FontWeight.Bold,color=Color.White);Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){f.forEach{x->Card(colors=CardDefaults.cardColors(containerColor=Navy2),shape=RoundedCornerShape(20.dp)){Column(Modifier.width(130.dp).padding(14.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(x.day,color=Soft,maxLines=2);WeatherIcon(x.condition,48);Text("${temp(x.min)} / ${temp(x.max)}",color=Color.White,fontWeight=FontWeight.Bold);Text(x.condition,color=Soft,fontSize=10.sp,maxLines=2)}}};if(f.isEmpty())Text("Prognoza ANM nu este disponibilă.",color=Soft)}}}
@Composable fun StationRow(x:Station,vm:VM){val fav=rememberFav(LocalContext.current);Row(Modifier.fillMaxWidth().clickable{vm.select(x.name)}.padding(16.dp),verticalAlignment=Alignment.CenterVertically){WeatherIcon(x.condition,38);Column(Modifier.weight(1f).padding(start=10.dp)){Text(x.name,color=Color.White);Text(x.condition,color=Soft,fontSize=11.sp)};Text(temp(x.temp),color=Color.White);IconButton({fav.toggle(x.name)}){Icon(if(fav.names.contains(x.name))Icons.Default.Star else Icons.Default.StarBorder,null,tint=if(fav.names.contains(x.name))Color.Yellow else Soft)}}}
@Composable fun Stations(vm:VM){var q by remember{mutableStateOf("")};Column{Header("Stații","Observații ANM");OutlinedTextField(q,{q=it},Modifier.padding(16.dp).fillMaxWidth(),label={Text("Caută")});LazyColumn{items(vm.s.stations.filter{it.name.contains(q,true)}){StationRow(it,vm)}}}}
class Fav(c:Context){val p=c.getSharedPreferences("fav",0);var names by mutableStateOf(p.getStringSet("n",emptySet())?.toSet()?:emptySet());fun toggle(n:String){names=if(names.contains(n))names-n else names+n;p.edit().putStringSet("n",names).apply()}}
@Composable fun rememberFav(c:Context)=remember{Fav(c)}
@Composable fun Favorites(vm:VM){val f=rememberFav(LocalContext.current);LazyColumn{item{Header("Favorite","Localități salvate")};items(vm.s.stations.filter{f.names.contains(it.name)}){StationRow(it,vm)}}}
@SuppressLint("SetJavaScriptEnabled") @Composable fun Radar(){Column{Header("Radar","Harta radar națională ANM");AndroidView({c->WebView(c).apply{settings.javaScriptEnabled=true;settings.domStorageEnabled=true;settings.cacheMode=android.webkit.WebSettings.LOAD_DEFAULT;webViewClient=WebViewClient();loadUrl("https://www.meteoromania.ro/radarm/radar.index.php")}},Modifier.fillMaxSize())}}
@Composable fun Alerts(vm:VM){val st=vm.s;LazyColumn{item{Header("Avertizări meteo","Generale și nowcasting la nivel național")};if(st.warningError!=null)item{Card(Modifier.padding(16.dp).fillMaxWidth()){Column(Modifier.padding(18.dp)){Text(st.warningError,color=Color.White);Text("Ultima verificare: ${st.warningChecked}",color=Soft);Button({vm.refresh(true)}){Text("Reîncearcă")}}}}else if(st.warnings.isEmpty())item{Card(Modifier.padding(16.dp).fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color(0xFF174C3D))){Column(Modifier.padding(18.dp)){Text("Nu există avertizări active în fluxurile ANM.",color=Color.White,fontWeight=FontWeight.Bold);Text("Verificat la ${st.warningChecked}",color=Soft)}}}else items(st.warnings){w->val col=when(w.level){"COD ROȘU"->Color.Red;"COD PORTOCALIU"->Color(0xFFFF8800);"COD GALBEN"->Color(0xFFFFC107);else->Blue};Card(Modifier.padding(16.dp,8.dp).fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=col)){Column(Modifier.padding(16.dp)){Text(w.level,fontWeight=FontWeight.Black);Text(w.title,fontWeight=FontWeight.Bold);Text(w.source,fontSize=11.sp);Text(w.body,maxLines=8)}}}}}
