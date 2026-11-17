package com.pediycc.mydrawer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pediycc.mydrawer.ui.DrawerScreen
import com.pediycc.mydrawer.ui.DrawerViewModel
import com.pediycc.mydrawer.ui.MyDrawerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyDrawerTheme {
                val vm: DrawerViewModel = viewModel()
                DrawerScreen(vm)
            }
        }
    }
}
