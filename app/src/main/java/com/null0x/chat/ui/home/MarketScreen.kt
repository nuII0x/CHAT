package com.null0x.chat.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.null0x.chat.market.MarketApi
import com.null0x.chat.market.MarketSnapshot

private enum class MarketPair(val label: String, val symbol: String) {
    BTC_USDT("BTC / USDT", "BTC/USDT"),
    XMR_USDT("XMR / USDT", "XMR/USDT")
}

private sealed interface MarketLoadState {
    data object Loading : MarketLoadState
    data class Ready(val snapshot: MarketSnapshot) : MarketLoadState
    data class Error(val message: String) : MarketLoadState
}

@Composable
internal fun MarketScreen(torReady: Boolean, networkAvailable: Boolean) {
    val context = LocalContext.current
    var pair by remember { mutableStateOf(MarketPair.BTC_USDT) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val marketState by produceState<MarketLoadState>(
        initialValue = MarketLoadState.Loading,
        key1 = torReady,
        key2 = networkAvailable,
        key3 = refreshKey
    ) {
        value = when {
            !networkAvailable -> MarketLoadState.Error("Sem conexão de rede")
            !MarketApi.isConfigured() -> MarketLoadState.Error("Servidor onion do Mercado ainda não configurado")
            !torReady -> MarketLoadState.Loading
            else -> runCatching { MarketApi.snapshot(context) }
                .fold(
                    onSuccess = { MarketLoadState.Ready(it) },
                    onFailure = { MarketLoadState.Error(it.message ?: "Servidor do Mercado indisponível") }
                )
        }
    }
    val snapshot = (marketState as? MarketLoadState.Ready)?.snapshot
    val quote = snapshot?.quotes?.get(pair.symbol)
    val lastPrice = quote?.formattedLast() ?: "—"
    val background = Color(0xFF0B1118)
    val panel = Color(0xFF121B24)
    val green = Color(0xFF29C783)
    val red = Color(0xFFFF5F6D)

    Surface(color = background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(pair.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text(lastPrice, color = green, fontFamily = FontFamily.Monospace, fontSize = 22.sp)
                }
                ConnectionBadge(
                    torReady = torReady,
                    networkAvailable = networkAvailable,
                    serverConnected = marketState is MarketLoadState.Ready
                )
            }

            when (val state = marketState) {
                MarketLoadState.Loading -> Text("Conectando ao servidor onion…", color = Color(0xFFFFC857))
                is MarketLoadState.Error -> Card(colors = CardDefaults.cardColors(containerColor = red.copy(alpha = 0.12f))) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(state.message, color = red, modifier = Modifier.weight(1f))
                        Button(onClick = { refreshKey++ }) { Text("Tentar") }
                    }
                }
                is MarketLoadState.Ready -> Text(
                    if (state.snapshot.mode == "simulation") "Servidor conectado • simulação" else "Servidor conectado",
                    color = green,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarketPair.entries.forEach { candidate ->
                    Button(
                        onClick = { pair = candidate },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (pair == candidate) Color(0xFF26384A) else panel
                        )
                    ) { Text(candidate.label) }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = panel)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("M15", color = Color(0xFFA7B3C2))
                        Text(snapshot?.mode?.uppercase() ?: "OFFLINE", color = green, fontFamily = FontFamily.Monospace)
                    }
                    DemoCandles(green, red)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuotePanel("ÚLTIMO", lastPrice, green, Modifier.weight(1f))
                QuotePanel("ATIVO", pair.symbol.substringBefore('/'), red, Modifier.weight(1f))
            }

            Card(colors = CardDefaults.cardColors(containerColor = panel)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Livro de ofertas", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Preço (USDT)        Quantidade", color = Color(0xFF8392A5), fontFamily = FontFamily.Monospace)
                    Text(
                        if (snapshot == null) "Aguardando servidor…" else "Sem ordens publicadas pelo servidor",
                        color = Color(0xFF8392A5)
                    )
                }
            }

            Text(
                "Ambiente de demonstração. Ordens, depósitos e retiradas permanecem bloqueados até a conexão autenticada com o servidor custodial.",
                color = Color(0xFFFFC857),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ConnectionBadge(torReady: Boolean, networkAvailable: Boolean, serverConnected: Boolean) {
    val (label, color) = when {
        !networkAvailable -> "SEM REDE" to Color(0xFFFF5F6D)
        serverConnected -> "ONION OK" to Color(0xFF29C783)
        torReady -> "TOR PRONTO" to Color(0xFFFFC857)
        else -> "CONECTANDO" to Color(0xFFFFC857)
    }
    Box(Modifier.background(color.copy(alpha = 0.16f), RoundedCornerShape(8.dp)).padding(8.dp)) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QuotePanel(label: String, value: String, color: Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = color, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(value, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 17.sp)
        }
    }
}

@Composable
private fun DemoCandles(green: Color, red: Color) {
    val values = listOf(0.62f, 0.48f, 0.55f, 0.35f, 0.42f, 0.28f, 0.32f, 0.20f, 0.26f, 0.15f)
    Canvas(Modifier.fillMaxWidth().height(190.dp).padding(top = 12.dp)) {
        repeat(5) { row ->
            val y = size.height * row / 4f
            drawLine(Color(0xFF26313E), Offset(0f, y), Offset(size.width, y), 1f)
        }
        val step = size.width / values.size
        values.forEachIndexed { index, value ->
            val next = values.getOrElse(index + 1) { value - 0.03f }
            val rising = next < value
            val color = if (rising) green else red
            val x = step * index + step / 2f
            val open = size.height * value
            val close = size.height * next
            drawLine(color, Offset(x, open - 16f), Offset(x, close + 16f), 2f)
            drawLine(color, Offset(x, open), Offset(x, close), step * 0.34f, StrokeCap.Square)
        }
    }
}
