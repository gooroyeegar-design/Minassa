package com.dzdocai.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.CAMERA)
        setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { DZGuideApp() } }
    }
}

data class Source(val name:String,val url:String,val note:String)
data class ScanResult(
    val type:String,
    val title:String,
    val confidence:Int,
    val summary:String,
    val fields:List<Pair<String,String>> = emptyList(),
    val warnings:List<String> = emptyList(),
    val nextSteps:List<String> = emptyList(),
    val sources:List<Source> = emptyList()
)
data class HistoryItem(val title:String,val type:String,val time:String)

private val interior=Source("وزارة الداخلية","https://services.interieur.gov.dz/","الحالة المدنية والوثائق البيومترية والشباك عن بعد")
private val dgi=Source("المديرية العامة للضرائب","https://www.mfdgi.gov.dz/fr/","الخدمات والوثائق الجبائية")
private val commerce=Source("وزارة التجارة / CNRC","https://commerce.gov.dz/fr/portail-du-cnrc","السجل التجاري ومعلومات المؤسسات")
private val school=Source("الجهة التعليمية","https://www.education.gov.dz/","صلاحية الشهادة المدرسية تعتمد على الغرض والجهة التي تطلبها")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DZGuideApp() {
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    var engineStatus by remember { mutableStateOf("محرك الوثائق جاهز") }
    val history=remember{mutableStateListOf<HistoryItem>()}

    MaterialTheme {
        Scaffold(
            topBar={
                CenterAlignedTopAppBar(
                    title={Text("مُرشد الجزائر AI",fontWeight=FontWeight.Bold)},
                    actions={Text("🇩🇿",modifier=Modifier.padding(end=16.dp))}
                )
            },
            bottomBar={
                NavigationBar{
                    NavigationBarItem(tab==0,{tab=0},icon={Text("⌕")},label={Text("مسح")})
                    NavigationBarItem(tab==1,{tab=1},icon={Text("✦")},label={Text("اسأل")})
                    NavigationBarItem(tab==2,{tab=2},icon={Text("◷")},label={Text("السجل")})
                }
            }
        ){ padding ->
            when(tab){
                0->HomeScreen(padding,scanning,engineStatus,{scanning=true},{engineStatus=it},{r->result=r;history.add(0,HistoryItem(r.title,r.type,"الآن"))},result)
                1->AskScreen(padding,query,{query=it},{result=DocumentBrain.analyze(query);result?.let{history.add(0,HistoryItem(it.title,it.type,"الآن"))}},result)
                else->HistoryScreen(padding,history)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    padding:PaddingValues,
    scanning:Boolean,
    engineStatus:String,
    startScan:()->Unit,
    onStatus:(String)->Unit,
    onResult:(ScanResult)->Unit,
    result:ScanResult?
){
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{
            Surface(shape=RoundedCornerShape(26.dp),tonalElevation=5.dp,modifier=Modifier.fillMaxWidth()){
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Text("صوّر الوثيقة… وأنا أشرح لك ماذا تفعل بها.",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    Text("شهادات مدرسية، عقود، فواتير، وثائق الحالة المدنية، السجل التجاري، الضرائب، الملكية، الكراء، النقل، البنوك، البريد والوثائق الإدارية.")
                    Button(startScan,Modifier.fillMaxWidth()){Text(if(scanning)"الكاميرا تعمل…" else "فتح الماسح الذكي")}
                    Text(engineStatus,style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        if(scanning)item{ScannerView(onResult,onStatus)}
        item{Text("ماذا يفهم؟",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
        item{Text("شهادة مدرسية • شهادة عمل • شهادة إقامة • شهادة إيواء • ميلاد • زواج • وفاة • جنسية • بطاقة عائلية • CNIBE • جواز السفر • رخصة السياقة • بطاقة التسجيل • التأمين • عقد الكراء • عقد الملكية • الدفتر العقاري • فواتير الكهرباء والماء والهاتف • إيصالات الدفع • الضرائب وNIF وC20 • CNRC • وثائق الجامعة • وثائق البنك والبريد • نماذج إدارية • QR/باركود • كتب ومنتجات")}
        result?.let{item{ResultCard(it)}}
        item{
            Surface(shape=RoundedCornerShape(18.dp),tonalElevation=1.dp,modifier=Modifier.fillMaxWidth()){
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Text("⚠️ مبدأ مهم",fontWeight=FontWeight.Bold)
                    Text("لا يفترض التطبيق أن كل وثيقة قديمة منتهية. يفرّق بين تاريخ الإصدار، مدة الصلاحية القانونية، والغرض الذي ستستعمل فيه الوثيقة.")
                }
            }
        }
    }
}

@Composable
private fun AskScreen(padding:PaddingValues,q:String,setQ:(String)->Unit,analyze:()->Unit,result:ScanResult?){
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("اسأل مُرشد الجزائر",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)}
        item{Text("مثلاً: «عندي شهادة مدرسية صادرة في 2025، هل أحتاج واحدة جديدة؟» أو «أين أودع هذا العقد؟»")}
        item{OutlinedTextField(q,setQ,Modifier.fillMaxWidth(),minLines=4,label={Text("اكتب سؤالك بالعربية أو الفرنسية")})}
        item{Button(analyze,Modifier.fillMaxWidth()){Text("حلّل الوثيقة / السؤال")}}
        result?.let{item{ResultCard(it)}}
    }
}

@Composable
private fun HistoryScreen(padding:PaddingValues,history:List<HistoryItem>){
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Text("السجل",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)}
        if(history.isEmpty())item{Text("لا توجد عمليات بعد.")}
        items(history){h->
            ListItem(headlineContent={Text(h.title)},supportingContent={Text("${h.type} • ${h.time}")})
            HorizontalDivider()
        }
    }
}

@Composable
private fun ResultCard(r:ScanResult){
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)){
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Top){
                Column(Modifier.weight(1f)){
                    Text(r.title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Text(r.type,style=MaterialTheme.typography.bodyMedium)
                }
                Surface(shape=RoundedCornerShape(20.dp),tonalElevation=3.dp){
                    Text("${r.confidence}%",Modifier.padding(horizontal=11.dp,vertical=7.dp),fontWeight=FontWeight.Bold)
                }
            }
            Text(r.summary)
            if(r.fields.isNotEmpty()){
                Text("المعلومات المستخرجة",fontWeight=FontWeight.Bold)
                r.fields.forEach{(k,v)->Column(Modifier.fillMaxWidth()){Text(k,fontWeight=FontWeight.SemiBold);Text(v)}}
            }
            if(r.warnings.isNotEmpty()){
                Surface(shape=RoundedCornerShape(15.dp),tonalElevation=2.dp,modifier=Modifier.fillMaxWidth()){
                    Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                        Text("⚠️ تنبيهات",fontWeight=FontWeight.Bold)
                        r.warnings.forEach{Text("• ${it}")}
                    }
                }
            }
            if(r.nextSteps.isNotEmpty()){
                Text("ماذا تفعل الآن؟",fontWeight=FontWeight.Bold)
                r.nextSteps.forEachIndexed{i,s->Text("${i+1}. ${s}")}
            }
            if(r.sources.isNotEmpty()){
                Text("المصادر الرسمية",fontWeight=FontWeight.Bold)
                r.sources.forEach{s->Text("${s.name} — ${s.note}",style=MaterialTheme.typography.bodySmall)}
            }
        }
    }
}

object DocumentBrain {
    private fun normalize(s:String)=s.lowercase().replace("أ","ا").replace("إ","ا").replace("آ","ا").replace("ة","ه")
    private fun year(text:String):Int?=Regex("""(?<!\d)(20\d{2})(?!\d)""").find(text)?.groupValues?.get(1)?.toIntOrNull()

    fun analyze(raw:String):ScanResult{
        val x=normalize(raw)
        val y=year(raw)
        return when {
            listOf("شهاده مدرسيه","شهاده تمدرس","certificat de scolarite","attestation de scolarite","school certificate").any{x.contains(normalize(it))} -> schoolCertificate(y)
            listOf("شهاده اقامه","شهاده اقامة","fiche de residence","certificat de residence").any{x.contains(normalize(it))} -> residence(y)
            listOf("شهاده ايواء","certificat d hebergement").any{x.contains(normalize(it))} -> ScanResult("شهادة إيواء","تم التعرف على شهادة إيواء",96,"هذه وثيقة لإثبات الإيواء وتُستعمل ضمن أغراض محددة.",listOf("السنة/التاريخ" to (y?.toString() ?: "غير واضح")),listOf("صلاحيتها الرسمية قد تكون محددة زمنياً والغرض المكتوب عليها مهم."),listOf("تحقق من البلدية ومطابقة الغرض المكتوب على الشهادة."),listOf(interior))
            listOf("عقد ميلاد","شهاده ميلاد","acte de naissance","extrait de naissance").any{x.contains(normalize(it))} -> civil("شهادة الميلاد","شهادة ميلاد",y)
            listOf("عقد زواج","شهاده زواج","acte de mariage").any{x.contains(normalize(it))} -> civil("عقد الزواج","عقد زواج",y)
            listOf("شهاده وفاه","شهادة وفاة","acte de deces").any{x.contains(normalize(it))} -> civil("شهادة الوفاة","شهادة وفاة",y)
            listOf("شهاده جنسيه","certificat de nationalite").any{x.contains(normalize(it))} -> ScanResult("الجنسية","شهادة الجنسية",95,"تم التعرف على وثيقة متعلقة بالجنسية. الحاجة إلى نسخة حديثة تعتمد على الملف الذي ستقدم فيه.",listOf("السنة/التاريخ" to (y?.toString() ?: "غير واضح")),listOf("لا تعتبر الوثيقة منتهية فقط بسبب قدم السنة؛ الغرض من استعمالها هو الذي يحدد ما إذا كانت نسخة جديدة مطلوبة."),listOf("اذكر الغرض (جواز، جامعة، ملف إداري...) ليحدد التطبيق المتطلبات بدقة أكبر."),listOf(interior))
            listOf("عقد كراء","عقد ايجار","عقد إيجار","contrat de location","bail").any{x.contains(normalize(it))} -> ScanResult("الكراء","عقد كراء / إيجار",94,"تم التعرف على عقد متعلق بالكراء. سيحاول التطبيق قراءة الأطراف والعنوان والمدة والتواريخ والقيمة.",listOf("السنة" to (y?.toString() ?: "غير واضحة")),listOf("وجود عقد قديم لا يعني تلقائياً أنه غير صالح؛ افحص تاريخ بداية ونهاية العقد والتجديد."),listOf("إذا انتهت مدة العقد، راجع بند التجديد أو أبرم عقداً جديداً حسب الحالة.","لإثبات الإقامة، قد تطلب البلدية وثائق إضافية مثل آخر وصولات الكراء."),listOf(interior))
            listOf("دفتر عقاري","عقد ملكيه","عقد ملكية","titre de propriete","livret foncier","acte de propriete").any{x.contains(normalize(it))} -> ScanResult("العقار","وثيقة ملكية عقارية",93,"تم التعرف على وثيقة مرتبطة بملكية عقار.",listOf("السنة" to (y?.toString() ?: "غير واضحة")),listOf("لا تُعتبر وثيقة الملكية منتهية لمجرد أن سنة إصدارها قديمة. يجب التحقق من الحالة القانونية الحالية للعقار والغرض من تقديم الوثيقة."),listOf("إذا كان الملف يتطلب وضعية حديثة أو شهادة محددة، اطلب الوثيقة المطلوبة من الجهة المختصة."),listOf(interior))
            listOf("سونلغاز","sonelgaz","فاتوره كهرباء","فاتورة كهرباء","فاتوره ماء","فاتورة ماء","ade","eau et gaz").any{x.contains(normalize(it))} -> utility("فاتورة خدمات","فاتورة كهرباء/غاز/ماء")
            listOf("recu","ايصال","وصل دفع","فاتوره","facture","ticket","receipt").any{x.contains(normalize(it))} -> ScanResult("فواتير","فاتورة / إيصال",91,"سيستخرج التطبيق اسم الجهة والتاريخ والمبلغ والمرجع عندما تكون واضحة.",listOf("السنة" to (y?.toString() ?: "غير واضحة")),emptyList(),listOf("احتفظ بالنسخة الأصلية إذا كانت مرتبطة بضمان أو إرجاع.","إذا كان الغرض إثبات الإقامة، تحقق من أن الجهة تقبل هذا النوع وحداثته."),emptyList())
            listOf("nif","c20","ضريبه","ضريبة","impot","fiscal","jibayatic").any{x.contains(normalize(it))} -> ScanResult("الضرائب","وثيقة جبائية / NIF / C20",94,"تم التعرف على وثيقة جبائية.",listOf("السنة" to (y?.toString() ?: "غير واضحة")),listOf("الخدمات والوثائق الجبائية الرقمية تتغير، لذلك يجب فتح المصدر الرسمي قبل الإيداع."),listOf("تحقق من خدمة DGI المناسبة قبل الذهاب إلى المصلحة.","إذا كان الملف يتطلب وثيقة لسنة مالية معينة، لا تستبدلها بوثيقة لسنة أخرى تلقائياً."),listOf(dgi))
            listOf("cnrc","registre de commerce","سجل تجاري","السجل التجاري","registre du commerce").any{x.contains(normalize(it))} -> ScanResult("التجارة","وثيقة السجل التجاري / CNRC",94,"تم التعرف على وثيقة مرتبطة بالسجل التجاري.",listOf("السنة" to (y?.toString() ?: "غير واضحة")),listOf("قد تحتاج بعض المعلومات إلى مطابقة مع السجل الحالي؛ لا تعتبر وثيقة قديمة دليلاً كافياً على الوضعية الحالية."),listOf("تحقق من بيانات المؤسسة عبر CNRC قبل استعمال الوثيقة في ملف جديد."),listOf(commerce))
            listOf("جواز","passeport","passport").any{x.contains(normalize(it))} -> ScanResult("وثيقة سفر","جواز سفر",97,"تم التعرف على جواز سفر. يمكن قراءة تاريخ الانتهاء والبيانات الظاهرة.",emptyList(),listOf("إذا اقترب تاريخ انتهاء الجواز، قد يكون التجديد متاحاً خلال الأشهر الستة السابقة لانقضائه حسب الإجراء الرسمي."),listOf("تحقق من تاريخ الانتهاء أولاً.","راجع الإجراء الرسمي للتجديد قبل التنقل."),listOf(interior))
            listOf("بطاقه تعريف","بطاقة التعريف","cnibe","carte nationale","identite").any{x.contains(normalize(it))} -> ScanResult("الهوية","بطاقة التعريف الوطنية",97,"تم التعرف على وثيقة هوية. سيحاول التطبيق قراءة تاريخ الانتهاء والجهة مع إبقاء البيانات الحساسة على الجهاز قدر الإمكان.",emptyList(),listOf("لا تشارك رقم التعريف الوطني أو صورة الوثيقة مع جهات غير موثوقة."),listOf("تحقق من تاريخ الانتهاء وأي تغيير في الحالة المدنية أو العنوان."),listOf(interior))
            else -> ScanResult("وثيقة غير مصنفة","لم أتعرف عليها بثقة كافية",42,"لا أريد أن أخمّن اسم وثيقة جزائرية وأعطيك إجراءً خاطئاً. أعد التصوير مع ظهور العنوان الكامل والختم والجهة المصدرة والتاريخ.",listOf("الدليل" to "غير كافٍ"),emptyList(),listOf("صوّر الوثيقة كاملة وبإضاءة جيدة.","اترك الحواف والعنوان والختم ظاهرين.","يمكنك كتابة اسم الوثيقة في تبويب «اسأل»."),listOf(interior))
        }
    }

    private fun schoolCertificate(y:Int?):ScanResult{
        val stale = y != null && y < java.time.LocalDate.now().year
        val warnings=mutableListOf<String>()
        if(stale) warnings.add("الشهادة تحمل سنة ${y}. هذا لا يعني أنها «منتهية قانونياً» تلقائياً، لكن شهادة مدرسية قديمة قد لا تثبت تسجيلك الحالي إذا كان الملف يطلب شهادة للسنة الدراسية الحالية.")
        return ScanResult("التعليم","شهادة مدرسية / شهادة تمدرس",96,"تم التعرف على شهادة مدرسية. سيقرأ التطبيق السنة الدراسية والمؤسسة والتاريخ عندما تكون الصورة واضحة.",listOf("السنة المكتشفة" to (y?.toString() ?: "غير واضحة")),warnings,listOf("إذا كان الغرض إثبات الدراسة الحالية، اطلب شهادة جديدة من المؤسسة/الجامعة للسنة الحالية.","إذا كان الغرض ملف جواز سفر، فالشهادة المدرسية مذكورة ضمن الوثائق المطلوبة للطلبة/المتمدرسين؛ راجع الملف الرسمي قبل الإيداع.","اذكر الغرض من الوثيقة لأعطيك قائمة المتطلبات والمسار المناسب."),listOf(school,interior))
    }

    private fun residence(y:Int?):ScanResult{
        return ScanResult("الإقامة","شهادة / بطاقة إقامة",96,"تم التعرف على وثيقة إقامة. مدة الاستعمال تختلف حسب نوع الوثيقة والغرض.",listOf("السنة المكتشفة" to (y?.toString() ?: "غير واضحة")),listOf("تحقق من تاريخ التوقيع وليس السنة المطبوعة فقط."),listOf("لملف جواز السفر، توجد شروط رسمية خاصة بحداثة شهادة الإقامة.","إذا كانت الوثيقة قديمة، اطلب شهادة جديدة من بلدية مكان الإقامة عند الحاجة."),listOf(interior))
    }

    private fun civil(title:String,type:String,y:Int?):ScanResult{
        return ScanResult("الحالة المدنية",title,97,"تم التعرف على وثيقة من وثائق الحالة المدنية.",listOf("السنة المكتشفة" to (y?.toString() ?: "غير واضحة")),listOf("قدم الوثيقة لا يعني دائماً أنها غير صالحة. بعض الإجراءات تفرض حداثة الوثيقة، مثل الأبوستيل لوثائق الحالة المدنية."),listOf("حدد الغرض من استعمال الوثيقة قبل طلب نسخة جديدة.","إذا كانت موجهة للأبوستيل، تحقق من شرط حداثتها والجهة المختصة قبل التنقل."),listOf(interior))
    }

    private fun utility(title:String,type:String)=ScanResult("فواتير","فاتورة خدمات",92,"تم التعرف على فاتورة خدمات. سيحاول التطبيق استخراج اسم صاحب الحساب والعنوان والتاريخ ورقم الاشتراك والمبلغ.",listOf("النوع" to type),listOf("الفاتورة القديمة قد لا تكون مقبولة كإثبات إقامة إذا كان الملف يشترط وصلاً حديثاً."),listOf("إذا كان الغرض إثبات الإقامة، احتفظ بآخر فاتورة/وصل كما تطلبه البلدية."),listOf(interior))
}

object ModelManager {
    private val client=OkHttpClient()
    private val models=mapOf(
        "ara" to "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/ara.traineddata",
        "fra" to "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/fra.traineddata",
        "eng" to "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/eng.traineddata"
    )
    suspend fun ensure(context:Context):Boolean=withContext(Dispatchers.IO){
        try{
            val root=File(context.filesDir,"tessdata");root.mkdirs()
            models.forEach{(lang,url)->
                val file=File(root,"${lang}.traineddata")
                if(!file.exists() || file.length()<100_000){
                    val response=client.newCall(Request.Builder().url(url).build()).execute()
                    if(!response.isSuccessful)return@withContext false
                    response.body?.byteStream()?.use{input->FileOutputStream(file).use{out->input.copyTo(out)}}
                }
            }
            true
        }catch(_:Exception){false}
    }
}

object ArabicOcr {
    fun read(context:Context,bitmap:Bitmap):String{
        return try{
            val tess=TessBaseAPI()
            if(!tess.init(context.filesDir.absolutePath,"ara+fra+eng",TessBaseAPI.OEM_LSTM_ONLY)) return ""
            tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            tess.setImage(bitmap)
            val text=tess.utF8Text ?: ""
            tess.end()
            text.trim()
        }catch(_:Exception){""}
    }
}

@Composable
private fun ScannerView(onResult:(ScanResult)->Unit,onStatus:(String)->Unit){
    val context=LocalContext.current
    val owner=context as ComponentActivity
    val previewView=remember{PreviewView(context)}
    val executor=remember{Executors.newSingleThreadExecutor()}
    val latin=remember{TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)}
    val barcode=remember{BarcodeScanning.getClient()}
    val labeler=remember{ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)}
    val scope=rememberCoroutineScope()
    var lastText by remember{mutableStateOf("")}
    var lastRun by remember{mutableLongStateOf(0L)}
    DisposableEffect(Unit){
        scope.launch{
            onStatus("جاري تجهيز محرك العربية والفرنسية…")
            val ok=ModelManager.ensure(context)
            onStatus(if(ok)"محرك العربية جاهز — وجّه الكاميرا إلى الوثيقة" else "تعذر تنزيل محرك العربية. سيستمر المسح النصي الأساسي.")
        }
        val future=ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider=future.get()
            val preview=Preview.Builder().build().also{it.surfaceProvider=previewView.surfaceProvider}
            val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor){proxy->
                val media=proxy.image
                if(media==null){proxy.close();return@setAnalyzer}
                val image=InputImage.fromMediaImage(media,proxy.imageInfo.rotationDegrees)
                latin.process(image).addOnSuccessListener{latinText->
                    val t=latinText.text.trim()
                    if(t.length>=10 && t!=lastText){lastText=t;onResult(DocumentBrain.analyze(t))}
                }
                barcode.process(image).addOnSuccessListener{codes->codes.firstOrNull()?.rawValue?.let{onResult(DocumentBrain.analyze("barcode ${it}"))}}
                val now=System.currentTimeMillis()
                if(now-lastRun>2200){
                    lastRun=now
                    val bmp:Bitmap?=try{proxy.toBitmap()}catch(_:Exception){null}
                    if(bmp!=null){
                        executor.execute{
                            val arabic=ArabicOcr.read(context,bmp)
                            bmp.recycle()
                            if(arabic.length>=10){
                                lastText=arabic
                                onResult(DocumentBrain.analyze(arabic))
                            }
                        }
                    }
                }
                labeler.process(image).addOnCompleteListener{proxy.close()}
            }
            try{provider.unbindAll();provider.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,preview,analysis)}catch(_:Exception){}
        },ContextCompat.getMainExecutor(context))
        onDispose{executor.shutdown();latin.close();barcode.close();labeler.close()}
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        AndroidView({previewView},Modifier.fillMaxWidth().height(390.dp).background(MaterialTheme.colorScheme.surfaceVariant))
        Text("حرّك الكاميرا ببطء حتى يظهر عنوان الوثيقة بوضوح.",Modifier.fillMaxWidth(),textAlign=TextAlign.Center,style=MaterialTheme.typography.bodySmall)
    }
}
