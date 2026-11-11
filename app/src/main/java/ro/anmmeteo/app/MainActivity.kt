package ro.anmmeteo.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.*

private val Navy=Color(0xFF071824); private val Navy2=Color(0xFF0C2535); private val Blue=Color(0xFF2196F3); private val Soft=Color(0xFFB6C7D4)
data class Station(val name:String,val temp:String,val wind:String,val humidity:String,val pressure:String,val cloud:String,val date:String,val lat:Double?,val lon:Double?)
data class Alert(val level:String,val title:String,val interval:String,val body:String)
data class UiState(val loading:Boolean=true,val stations:List<Station> = emptyList(),val selected:String="BRASOV GHIMBAV",val alerts:List<Alert> = emptyList(),val error:String?=null)

class WeatherViewModel:ViewModel(){
 var state by mutableStateOf(UiState()); private set
 private val http=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).build()
 init{refresh()}
 fun select(n:String){state=state.copy(selected=n)}
 fun selectNearest(lat:Double,lon:Double){state.stations.filter{it.lat!=null&&it.lon!=null}.minByOrNull{distance(lat,lon,it.lat!!,it.lon!!)}?.let{select(it.name)}}
 fun refresh(){viewModelScope.launch{state=state.copy(loading=true,error=null);runCatching{withContext(Dispatchers.IO){stations()}}.onSuccess{state=state.copy(loading=false,stations=it)}.onFailure{state=state.copy(loading=false,error="Datele ANM nu au putut fi încărcate: ${it.message}")};launch{runCatching{withContext(Dispatchers.IO){alerts()}}.onSuccess{state=state.copy(alerts=it)}}}}
 private fun get(url:String)=http.newCall(Request.Builder().url(url).header("User-Agent","ANMMeteo/0.2 Android personal app").build()).execute().use{if(!it.isSuccessful)error("HTTP ${it.code}");it.body?.string()?:error("Răspuns gol")}
 private fun stations():List<Station>{val raw=get("https://www.meteoromania.ro/wp-json/meteoapi/v2/starea-vremii");val root=if(raw.trim().startsWith("["))JSONArray(raw)else JSONObject(raw).optJSONArray("features")?:JSONObject(raw).optJSONArray("data")?:JSONArray();val out=mutableListOf<Station>();for(i in 0 until root.length()){val base=root.optJSONObject(i)?:continue;val o=base.optJSONObject("properties")?:base;fun f(vararg k:String):String{for(x in k){val v=o.opt(x);if(v!=null&&v.toString()!="null"&&v.toString().isNotBlank())return v.toString()};return "—"};fun d(vararg k:String):Double?=f(*k).replace(',','.').toDoubleOrNull();val coords=base.optJSONObject("geometry")?.optJSONArray("coordinates");val lon=coords?.optDouble(0)?.takeUnless{it.isNaN()}?:d("lon","lng","longitude","longitudine");val lat=coords?.optDouble(1)?.takeUnless{it.isNaN()}?:d("lat","latitude","latitudine");val n=f("nume","name","statie","station","denumire").uppercase(Locale.ROOT);if(n!="—")out+=Station(n,f("temperatura","temp","temperature","tempe"),f("vant","wind","viteza_vant"),f("umezeala","umiditate","humidity","umezeala_relativa"),f("presiune","pressure"),f("nebulozitate","cloud","stare_vreme","fenomen"),f("data","date","timestamp","ora"),lat,lon)};if(out.isEmpty())error("Format ANM necunoscut");return out.sortedBy{it.name}}
 private fun alerts():List<Alert>{val xml=get("https://www.meteoromania.ro/avertizari-xml.php");fun tag(s:String,n:String)=Regex("<$n[^>]*>(?:<!\\[CDATA\\[)?([\\s\\S]*?)(?:]]>)?</$n>",RegexOption.IGNORE_CASE).find(s)?.groupValues?.get(1)?.replace(Regex("<[^>]+>")," ")?.trim().orEmpty();return Regex("<item[\\s\\S]*?</item>",RegexOption.IGNORE_CASE).findAll(xml).map{it.value}.map{b->val t=tag(b,"title");val desc=tag(b,"description");val u=(t+desc).uppercase();Alert(when{u.contains("ROȘU")||u.contains("ROSU")->"COD ROȘU";u.contains("PORTOCALIU")->"COD PORTOCALIU";u.contains("GALBEN")->"COD GALBEN";else->"INFORMARE"},t.ifBlank{"Avertizare meteorologică"},tag(b,"pubDate"),desc)}.take(20).toList()}
 private fun distance(a:Double,b:Double,c:Double,d:Double):Double{val r=6371.0;val p1=Math.toRadians(a);val p2=Math.toRadians(c);val dp=Math.toRadians(c-a);val dl=Math.toRadians(d-b);val x=sin(dp/2).pow(2)+cos(p1)*cos(p2)*sin(dl/2).pow(2);return 2*r*atan2(sqrt(x),sqrt(1-x))}
}

class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContent{App()}}}
enum class Tab{HOME,STATIONS,RADAR,ALERTS,FAVORITES}
@Composable fun App(vm:WeatherViewModel= viewModel()){var tab by remember{mutableStateOf(Tab.HOME)};MaterialTheme(colorScheme=darkColorScheme(primary=Blue,background=Navy,surface=Navy2,onSurface=Color.White,onBackground=Color.White)){Scaffold(containerColor=Navy,bottomBar={NavigationBar(containerColor=Color(0xFF081D2A)){nav(Tab.HOME,tab,"Acasă",Icons.Default.Home){tab=it};nav(Tab.STATIONS,tab,"Stații",Icons.Default.Place){tab=it};nav(Tab.RADAR,tab,"Radar",Icons.Default.Map){tab=it};nav(Tab.ALERTS,tab,"Alerte",Icons.Default.Warning){tab=it};nav(Tab.FAVORITES,tab,"Favorite",Icons.Default.Star){tab=it}}}){p->Box(Modifier.padding(p).fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF123C56),Navy)))){when(tab){Tab.HOME->Home(vm);Tab.STATIONS->Stations(vm);Tab.RADAR->Radar();Tab.ALERTS->Alerts(vm);Tab.FAVORITES->Favorites(vm)}}}}}
@Composable fun RowScope.nav(t:Tab,c:Tab,l:String,i:androidx.compose.ui.graphics.vector.ImageVector,on:(Tab)->Unit){NavigationBarItem(selected=t==c,onClick={on(t)},icon={Icon(i,l)},label={Text(l,fontSize=10.sp)})}
@Composable fun Header(t:String,s:String){Column(Modifier.fillMaxWidth().padding(18.dp,16.dp,18.dp,8.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(t,24.sp,fontWeight=FontWeight.Bold);Text(s,color=Soft)}}
@Composable fun Home(vm:WeatherViewModel){val st=vm.state;val cur=st.stations.firstOrNull{it.name==st.selected}?:st.stations.firstOrNull();val context=LocalContext.current;val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){if(it.values.any{v->v})locate(context,vm)};LazyColumn(contentPadding=PaddingValues(bottom=20.dp)){item{Header("ANM Meteo","România, mereu la zi")};item{if(st.loading)LinearProgressIndicator(Modifier.fillMaxWidth())};item{Row(Modifier.padding(horizontal=16.dp).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)locate(context,vm)else launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))},modifier=Modifier.weight(1f)){Icon(Icons.Default.MyLocation,null);Spacer(Modifier.width(8.dp));Text("Locația mea")};OutlinedButton(onClick=vm::refresh){Icon(Icons.Default.Refresh,null)}}};item{if(cur!=null)Current(cur)else Error(st.error?:"Se încarcă datele ANM",vm::refresh)};item{Text("Stații meteo",Modifier.padding(20.dp,16.dp,20.dp,6.dp),fontWeight=FontWeight.Bold)};items(st.stations.take(6)){StationRow(it,vm)}}}
@SuppressLint("MissingPermission") private fun locate(c:Context,vm:WeatherViewModel){val lm=c.getSystemService(Context.LOCATION_SERVICE)as LocationManager;val providers=listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER);val last=providers.mapNotNull{runCatching{lm.getLastKnownLocation(it)}.getOrNull()}.maxByOrNull{it.time};if(last!=null)vm.selectNearest(last.latitude,last.longitude);providers.forEach{p->runCatching{lm.requestSingleUpdate(p,object:android.location.LocationListener{override fun onLocationChanged(l:Location){vm.selectNearest(l.latitude,l.longitude)}},null)}}}
@Composable fun Current(s:Station){Card(Modifier.padding(16.dp).fillMaxWidth(),shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=Color(0xB3123448))){Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(s.name.replace(" GHIMBAV",""),22.sp,fontWeight=FontWeight.Bold);Text(if(s.date=="—")"Date oficiale ANM" else "Actualizat: ${s.date}",color=Soft);Icon(Icons.Default.WbCloudy,null,tint=Color(0xFFFFD34D),modifier=Modifier.padding(16.dp).size(70.dp));Text(temp(s.temp),64.sp,fontWeight=FontWeight.Light);Text(s.cloud,20.sp);Spacer(Modifier.height(20.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceAround){Metric("Umiditate",s.humidity,"%");Metric("Vânt",s.wind,"");Metric("Presiune",s.pressure," hPa")}}}}
fun temp(v:String)=Regex("-?\\d+(?:[.,]\\d+)?").find(v)?.value?.let{"${it.replace(',','.')}°C"}?:v
@Composable fun Metric(k:String,v:String,s:String){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(k,color=Soft,fontSize=12.sp);Text(if(v=="—"||s.isBlank()||v.contains(s.trim(),true))v else v+s,fontWeight=FontWeight.Bold)}}
@Composable fun Error(m:String,r:()->Unit){Card(Modifier.padding(16.dp).fillMaxWidth()){Column(Modifier.padding(18.dp)){Text(m);Button(r,Modifier.padding(top=10.dp)){Text("Reîncearcă")}}}}
@Composable fun StationRow(s:Station,vm:WeatherViewModel){val context=LocalContext.current;val fav=rememberFavorites(context);Row(Modifier.fillMaxWidth().clickable{vm.select(s.name)}.padding(18.dp,12.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Cloud,null,tint=Color(0xFF90CAF9));Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(s.name,fontWeight=FontWeight.SemiBold);Text(s.cloud,color=Soft,fontSize=12.sp)};Text(temp(s.temp),fontSize=20.sp);IconButton(onClick={fav.toggle(s.name)}){Icon(if(fav.names.contains(s.name))Icons.Default.Star else Icons.Default.StarBorder,null,tint=if(fav.names.contains(s.name))Color(0xFFFFD54F) else Soft)}};HorizontalDivider(color=Color.White.copy(.08f))}
@Composable fun Stations(vm:WeatherViewModel){var q by remember{mutableStateOf("")};Column{Header("Stații meteo","Observații oficiale ANM");OutlinedTextField(q,{q=it},Modifier.padding(16.dp).fillMaxWidth(),label={Text("Caută localitate")},leadingIcon={Icon(Icons.Default.Search,null)});LazyColumn{items(vm.state.stations.filter{it.name.contains(q,true)}){StationRow(it,vm)}}}}
@Composable fun Favorites(vm:WeatherViewModel){val c=LocalContext.current;val fav=rememberFavorites(c);val list=vm.state.stations.filter{fav.names.contains(it.name)};LazyColumn{item{Header("Localități favorite","Salvate pe acest telefon")};if(list.isEmpty())item{Text("Adaugă localități apăsând steaua din lista de stații.",Modifier.padding(24.dp),color=Soft)}else items(list){StationRow(it,vm)}}}
class FavoriteStore(private val c:Context){var names by mutableStateOf(c.getSharedPreferences("favorites",0).getStringSet("names",emptySet())?.toSet()?:emptySet());private set;fun toggle(n:String){names=if(names.contains(n))names-n else names+n;c.getSharedPreferences("favorites",0).edit().putStringSet("names",names).apply()}}
@Composable fun rememberFavorites(c:Context):FavoriteStore=remember{FavoriteStore(c)}
@SuppressLint("SetJavaScriptEnabled") @Composable fun Radar(){Column{Header("Radar precipitații","Harta radar națională ANM");AndroidView(factory={c->WebView(c).apply{settings.javaScriptEnabled=true;settings.domStorageEnabled=true;settings.setSupportZoom(true);settings.builtInZoomControls=true;settings.displayZoomControls=false;webViewClient=WebViewClient();webChromeClient=WebChromeClient();loadUrl("https://www.meteoromania.ro/radarm/radar.index.php")}},modifier=Modifier.fillMaxSize(),update={})}}
@Composable fun Alerts(vm:WeatherViewModel){LazyColumn{item{Header("Avertizări meteo","Mesaje oficiale ANM")};if(vm.state.alerts.isEmpty())item{Error("Nu sunt avertizări disponibile sau fluxul ANM nu a răspuns.",vm::refresh)}else items(vm.state.alerts){AlertCard(it)}}}
@Composable fun AlertCard(a:Alert){val c=when(a.level){"COD ROȘU"->Color(0xFFD32F2F);"COD PORTOCALIU"->Color(0xFFF57C00);"COD GALBEN"->Color(0xFFFBC02D);else->Color(0xFF1976D2)};val fg=if(a.level=="COD GALBEN")Color.Black else Color.White;Card(Modifier.padding(16.dp,8.dp).fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=c),shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(18.dp)){Text(a.level,fontWeight=FontWeight.Black,fontSize=19.sp,color=fg);Text(a.title,fontWeight=FontWeight.Bold,color=fg);if(a.interval.isNotBlank())Text(a.interval,fontSize=12.sp,color=fg.copy(.8f));if(a.body.isNotBlank())Text(a.body,maxLines=6,color=fg.copy(.9f))}}}
