package com.pureguard.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pureguard.app.ui.theme.Navy
import com.pureguard.app.ui.theme.NavyDeep
import com.pureguard.app.ui.theme.PureGuardTheme
import com.pureguard.app.ui.theme.TealBright

/** Full-screen, calm block page shown whenever something is stopped. */
class BlockedActivity : ComponentActivity() {

    companion object {
        const val EXTRA_REASON = "reason"

        private val QUOTES = listOf(
            "Every urge you ride out makes the next one weaker.",
            "You're building the life you actually want. Keep going.",
            "Discipline is choosing what you want most over what you want now.",
            "This moment will pass. Your progress stays.",
            "Breathe. Stand up. Drink some water. Go for a short walk.",
            "You set this up because you believe in yourself. So do we.",
            "Strong people aren't free of urges — they just don't obey them.",
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "Content blocked"
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        setContent {
            PureGuardTheme {
                BlockedScreen(reason, remember { QUOTES.random() }, ::goHome)
            }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }
}

@Composable
private fun BlockedScreen(reason: String, quote: String, onHome: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Navy, NavyDeep))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(120.dp)
                    .background(TealBright.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Shield, null, tint = TealBright, modifier = Modifier.size(64.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "Blocked by PureGuard",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(reason, color = TealBright, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            Text(
                "“$quote”",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onHome,
                colors = ButtonDefaults.buttonColors(containerColor = TealBright, contentColor = NavyDeep),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Take me back home")
            }
        }
    }
}
