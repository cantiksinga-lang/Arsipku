package com.alhafidz.app.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

suspend fun recognizeReceipt(context:Context,uri:Uri):String {
 val image=InputImage.fromFilePath(context,uri)
 return TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image).await().text
}
fun parseReceipt(text:String):ReceiptDraft {
 val lines=text.lines().map{it.trim()}.filter{it.isNotBlank()}
 val money=Regex("(?i)(?:rp\\.?\\s*)?([0-9][0-9.,]{2,})")
 val candidates=lines.mapNotNull{line->money.findAll(line).lastOrNull()?.groupValues?.get(1)?.replace(".","")?.replace(",","")?.toLongOrNull()?.let{line to it}}
 val total=candidates.maxByOrNull{it.second}?.second ?: 0L
 val item=lines.firstOrNull{it.length>3 && !money.containsMatchIn(it) && !it.contains("total",true) && !it.contains("tanggal",true)} ?: "Servis / Sparepart"
 val category=when { text.contains("bensin",true)||text.contains("pertalite",true)||text.contains("pertamax",true)||text.contains("liter",true)->"BBM"; text.contains("oli",true)||text.contains("service",true)||text.contains("servis",true)->"Servis"; else->"Sparepart" }
 return ReceiptDraft(item,total,category,text)
}
data class ReceiptDraft(val item:String,val amount:Long,val category:String,val rawText:String)
