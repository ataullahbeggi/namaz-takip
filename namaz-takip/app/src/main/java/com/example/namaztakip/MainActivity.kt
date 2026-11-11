package com.example.namaztakip

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

val PRAYERS = listOf("Sabah", "Öğle", "İkindi", "Akşam", "Yatsı")

/** Başlangıç tarihi ve işaretlenen namazları cihazda saklar. */
class PrayerStore(context: Context) {
    private val prefs = context.getSharedPreferences("namaz_takip", Context.MODE_PRIVATE)

    var startDate by mutableStateOf(
        prefs.getString("start_date", null)?.let { LocalDate.parse(it) }
    )
        private set

    // Anahtar biçimi: "2026-10-13|2" (tarih|vakit indeksi)
    private var checked by mutableStateOf(prefs.getStringSet("checked", emptySet()) ?: emptySet())

    fun setStartDate(date: LocalDate) {
        startDate = date
        prefs.edit().putString("start_date", date.toString()).apply()
    }

    fun isChecked(date: LocalDate, prayer: Int) = "$date|$prayer" in checked

    fun setChecked(date: LocalDate, prayer: Int, value: Boolean) {
        val key = "$date|$prayer"
        checked = if (value) checked + key else checked - key
        prefs.edit().putStringSet("checked", checked).apply()
    }

    /** Başlangıç tarihinden bugüne (bugün dahil) gün sayısı. */
    fun dayCount(today: LocalDate): Int {
        val start = startDate ?: return 0
        return (ChronoUnit.DAYS.between(start, today).toInt() + 1).coerceAtLeast(0)
    }

    /** Bir vaktin kalan borcu = geçen gün sayısı - kılınmış (işaretli) sayısı. */
    fun debt(prayer: Int, today: LocalDate): Int {
        val start = startDate ?: return 0
        val days = dayCount(today)
        val done = (0 until days).count { isChecked(start.plusDays(it.toLong()), prayer) }
        return days - done
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = PrayerStore(applicationContext)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NamazApp(store)
                }
            }
        }
    }
}

@Composable
fun NamazApp(store: PrayerStore) {
    var showDays by remember { mutableStateOf(false) }
    BackHandler(enabled = showDays) { showDays = false }
    if (showDays) {
        DaysScreen(store, onBack = { showDays = false })
    } else {
        HomeScreen(store, onOpenDays = { showDays = true })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(store: PrayerStore, onOpenDays: () -> Unit) {
    val today = LocalDate.now()
    val start = store.startDate
    var showPicker by remember { mutableStateOf(false) }
    val fmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("tr"))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Namaz Borcu", fontSize = 28.sp, fontWeight = FontWeight.Bold)

        if (start == null) {
            Text("Başlamak için bir başlangıç tarihi seç.")
        } else {
            Text("Başlangıç: ${start.format(fmt)}")
            val total = PRAYERS.indices.sumOf { store.debt(it, today) }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Toplam borç", fontSize = 14.sp)
                    Text("$total", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                }
            }
            PRAYERS.forEachIndexed { index, name ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(name, fontSize = 18.sp)
                        Text("${store.debt(index, today)}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Button(onClick = onOpenDays, modifier = Modifier.fillMaxWidth()) {
                Text("Günlük Takip")
            }
        }

        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (start == null) "Başlangıç Tarihi Seç" else "Başlangıç Tarihini Değiştir")
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (start ?: today)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val picked = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        store.setStartDate(if (picked.isAfter(today)) today else picked)
                    }
                    showPicker = false
                }) { Text("Tamam") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("İptal") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
fun DaysScreen(store: PrayerStore, onBack: () -> Unit) {
    val today = LocalDate.now()
    val start = store.startDate ?: return
    val days = store.dayCount(today)
    val fmt = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", Locale("tr"))

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹ Geri") }
            Text("Günlük Takip", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // En yeni gün en üstte
            items((days - 1 downTo 0).toList()) { offset ->
                val date = start.plusDays(offset.toLong())
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = date.format(fmt) + if (date == today) " (Bugün)" else "",
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        PRAYERS.forEachIndexed { index, name ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = store.isChecked(date, index),
                                    onCheckedChange = { store.setChecked(date, index, it) }
                                )
                                Text(name, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
