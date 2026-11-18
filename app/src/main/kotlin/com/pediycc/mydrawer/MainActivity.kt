package com.pediycc.mydrawer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pediycc.mydrawer.ui.DrawerScreen
import com.pediycc.mydrawer.ui.DrawerViewModel
import com.pediycc.mydrawer.ui.MyDrawerTheme

class MainActivity : ComponentActivity() {
    private var drawerVm: DrawerViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyDrawerTheme {
                val vm: DrawerViewModel = viewModel()
                drawerVm = vm
                DrawerScreen(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 用户可能刚从系统「使用情况访问权限」页返回，重新读取真实使用频率。
        drawerVm?.reloadUsage()
    }
}
