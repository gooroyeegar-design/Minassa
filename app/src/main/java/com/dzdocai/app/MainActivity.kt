package com.dzdocai.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.CAMERA)
        setContent { DZGuideApp() }
    }
}

data class ScanResult(val type:String,val title:String,val confidence:Int,val summary:String,val fields:List<Pair<String,String>>=emptyList(),val nextSteps:List<String> = emptyList(),val sources:List<Source> = emptyList())
data class Source(val name:String,val url:String,val note:String)
data class HistoryItem(val title:String,val type:String,val time:String)

private val interior=Source("Ministry of Interior","https://services.interieur.gov.dz/","Civil status, biometric documents and remote administrative services")
private val dgi=Source("General Directorate of Taxes","https://www.mfdgi.gov.dz/fr/","Tax services and official tax guidance")
private val cnrc=Source("CNRC / Ministry of Commerce","https://commerce.gov.dz/fr/portail-du-cnrc","Business and commercial-register information")
private val dzair=Source("Dzair Digital Services","https://services.interieur.gov.dz/","National digital-service entry point; availability can change")

@Composable
fun DZGuideApp() {
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    val history=remember{mutableStateListOf<HistoryItem>()}
    MaterialTheme {
        Scaffold(topBar={CenterAlignedTopAppBar(title={Text("DZ Guide AI",fontWeight=FontWeight.Bold)},actions={Text("DZ",modifier=Modifier.padding(end=16.dp),fontWeight=FontWeight.Bold)})},
            bottomBar={NavigationBar{
                NavigationBarItem(tab==0,{tab=0},icon={Text("⌕")},label={Text("Scan")})
                NavigationBarItem(tab==1,{tab=1},icon={Text("✦")},label={Text("Ask")})
                NavigationBarItem(tab==2,{tab=2},icon={Text("◷")},label={Text("History")})
            }}) { padding ->
            when(tab){
                0->HomeScreen(padding,scanning,{scanning=true},{scanning=false;result=it;history.add(0,HistoryItem(it.title,it.type,"Just now"))},result)
                1->AskScreen(padding,query,{query=it},{result=DemoEngine.answer(query);result?.let{history.add(0,HistoryItem(it.title,it.type,"Just now"))}},result)
                else->HistoryScreen(padding,history)
            }
        }
    }
}

@Composable
private fun HomeScreen(padding:PaddingValues,scanning:Boolean,startScan:()->Unit,onResult:(ScanResult)->Unit,result:ScanResult?){
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{Surface(shape=RoundedCornerShape(24.dp),tonalElevation=4.dp,modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Scan. Understand. Act.",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
            Text("Your visual assistant for Algerian documents, receipts, books, forms, labels and everyday things.")
            Button({startScan()},Modifier.fillMaxWidth()){Text(if(scanning)"Scanning…" else "Open smart scanner")}
        }}}
        if(scanning)item{ScannerView(onResult)}
        item{Text("What it can recognize",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
        item{Text("Receipts • books • QR/barcodes • CNIBE/passport clues • civil-status documents • forms • tax documents • products • objects")}
        result?.let{item{ResultCard(it)}}
        item{Text("Official-source first",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)}
        item{Text("For government procedures, the app separates what it detected from what an official source currently says.")}
    }
}

@Composable
private fun AskScreen(padding:PaddingValues,q:String,setQ:(String)->Unit,analyze:()->Unit,result:ScanResult?){
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("Ask DZ Guide",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)}
        item{Text("Try: “Where do I submit a birth certificate request?”, “What is this receipt?”, “What is this tax document?”")}
        item{OutlinedTextField(q,setQ,Modifier.fillMaxWidth(),minLines=3,label={Text("Describe or paste what you see")})}
        item{Button(analyze,Modifier.fillMaxWidth()){Text("Analyze")}}
        result?.let{item{ResultCard(it)}}
    }
}

@Composable
private fun HistoryScreen(padding:PaddingValues,history:List<HistoryItem>){
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Text("Recent scans",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)}
        if(history.isEmpty())item{Text("Nothing yet. Your scan history stays local in this version.")}
        items(history){h->ListItem(headlineContent={Text(h.title)},supportingContent={Text("\${h.type} • \${h.time}")});HorizontalDivider()}
    }
}

@Composable
private fun ResultCard(r:ScanResult){
    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            Column(Modifier.weight(1f)){Text(r.title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(r.type)}
            Surface(shape=RoundedCornerShape(20.dp),tonalElevation=3.dp){Text("\${r.confidence}%",Modifier.padding(horizontal=10.dp,vertical=6.dp),fontWeight=FontWeight.Bold)}
        }
        Text(r.summary)
        if(r.fields.isNotEmpty()){Text("Extracted information",fontWeight=FontWeight.Bold);r.fields.forEach{(k,v)->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(k,fontWeight=FontWeight.SemiBold);Text(v,Modifier.padding(start=12.dp))}}}
        if(r.nextSteps.isNotEmpty()){Text("Next steps",fontWeight=FontWeight.Bold);r.nextSteps.forEach{Text("→ \$it")}}
        if(r.sources.isNotEmpty()){Text("Official / reference sources",fontWeight=FontWeight.Bold);r.sources.forEach{s->Text("\${s.name}: \${s.note}",Modifier.clickable{},style=MaterialTheme.typography.bodySmall)}}
    }}
}

object DemoEngine{
    fun answer(q:String):ScanResult{
        val x=q.lowercase()
        return when{
            listOf("receipt","facture","ticket","reçu").any{x.contains(it)}->ScanResult("Purchase receipt","Receipt detected",91,"A production scan will extract merchant, date, line items, total, tax clues, payment method and warranty/return dates when visible.",listOf("Type" to "Receipt","Status" to "Needs clear image for exact fields"),listOf("Keep the original receipt for returns or warranty.","If a warranty date is visible, save it as a reminder.","Never trust extracted totals without checking the image."))
            listOf("book","livre","page","isbn","roman").any{x.contains(it)}->ScanResult("Book / page","Book or page detected",86,"The scanner can combine OCR and ISBN/barcode data to identify a book, author, edition and available catalog metadata.",listOf("Type" to "Book/page","Lookup" to "ISBN or visible title"),listOf("Capture the cover or ISBN for stronger matching.","Only metadata is returned; copyrighted pages are not reproduced."))
            listOf("birth","naissance","acte de naissance","ميلاد").any{x.contains(it)}->ScanResult("Civil status","Birth-certificate request/document",89,"Algeria's Interior Ministry currently lists online civil-status services for birth, marriage and death certificates.",listOf("Domain" to "Civil status","Route" to "Check official online service first"),listOf("Open the official Interior Ministry civil-status service.","If your specific case requires an in-person step, follow the current official instructions."),listOf(interior))
            listOf("passport","passeport","جواز").any{x.contains(it)}->ScanResult("Biometric document","Passport-related document",88,"The Interior Ministry portal provides biometric-document services and request tracking.",listOf("Type" to "Passport","Action" to "Verify current official procedure"),listOf("Use the official Interior Ministry portal for the current process.","Do not send a passport image to an untrusted service."),listOf(interior))
            listOf("cnibe","identity","id card","carte nationale","بطاقة التعريف").any{x.contains(it)}->ScanResult("Identity document","Possible CNIBE-related document",88,"The Interior Ministry portal lists biometric/electronic identity services and CNIBE-related functionality.",listOf("Type" to "Identity document","Risk" to "Sensitive personal data"),listOf("Use official government channels for applications and tracking.","Avoid sharing full identity numbers publicly."),listOf(interior))
            listOf("tax","fiscal","impôt","nif","c20","ضريبة").any{x.contains(it)}->ScanResult("Tax document","Tax / NIF document",90,"The DGI has expanded online tax services through Dzair Digital Services, including NIF certificates, tax-roll extracts, non-taxation certificates and C20 certificates.",listOf("Domain" to "Tax","Source" to "DGI / Dzair Digital Services"),listOf("Check the current DGI service route before submitting anything.","Some tax portals and credentials may still require an in-person step."),listOf(dgi,dzair))
            listOf("commerce","cnrc","registre de commerce","سجل تجاري").any{x.contains(it)}->ScanResult("Business / CNRC","Commercial-register context",87,"The Ministry of Commerce's CNRC portal provides free searches for merchants/companies, activities and business names, with additional detailed services.",listOf("Domain" to "Commerce / CNRC"),listOf("Check the CNRC service matching your task.","Verify the exact procedure and fees on the official source."),listOf(cnrc))
            else->ScanResult("Unknown item","Needs a real scan",35,"The full vision pipeline combines OCR in Latin and Arabic, barcode/QR scanning, image labels and structured Algerian knowledge. A text-only guess is not enough.",listOf("Evidence" to "Insufficient"),listOf("Open Smart Scanner and fill the camera frame.","For administrative documents, use a sharp image showing the full heading and issuing authority."))
        }
    }
}

@Composable
private fun ScannerView(onResult:(ScanResult)->Unit){
    val context=LocalContext.current
    val owner=context as ComponentActivity
    val previewView=remember{PreviewView(context)}
    val executor=remember{Executors.newSingleThreadExecutor()}
    val latin=remember{TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)}
    val barcode=remember{BarcodeScanning.getClient()}
    val labeler=remember{ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)}
    var lastText by remember{mutableStateOf("")}
    DisposableEffect(Unit){
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
                    val combined=latinText.text.trim()
                    if(combined.length>=8 && combined!=lastText){lastText=combined;onResult(DemoEngine.answer(combined.take(1600)))}
                }
                barcode.process(image).addOnSuccessListener{codes->if(codes.isNotEmpty())onResult(DemoEngine.answer("barcode "+(codes.first().rawValue?:"")))}
                labeler.process(image).addOnSuccessListener{labels->labels.firstOrNull{it.confidence>=0.85f}?.let{if(lastText.isBlank())onResult(DemoEngine.answer(it.text))}}.addOnCompleteListener{proxy.close()}
            }
            try{provider.unbindAll();provider.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,preview,analysis)}catch(_:Exception){}
        },ContextCompat.getMainExecutor(context))
        onDispose{executor.shutdown();latin.close();barcode.close();labeler.close()}
    }
    AndroidView({previewView},Modifier.fillMaxWidth().height(380.dp))
}
