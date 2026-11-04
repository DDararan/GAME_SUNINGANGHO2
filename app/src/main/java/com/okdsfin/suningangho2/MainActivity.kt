package com.okdsfin.suningangho2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.okdsfin.suningangho2.ui.GameScreen
import com.okdsfin.suningangho2.ui.PixelTheme

class MainActivity : ComponentActivity() {
    private val game: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelTheme { GameScreen(game) }
        }
    }

    override fun onStart() {
        super.onStart()
        game.onStart()
    }

    override fun onStop() {
        super.onStop()
        game.onStop()
    }
}
