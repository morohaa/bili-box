package io.github.bilibox.feature.profile

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProfileLoginDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun routesEachLoginOptionSeparately() {
        val selections = mutableListOf<String>()
        compose.setContent {
            ProfileLoginDialog(
                state = LoginUiState.SignedOut,
                darkMode = false,
                onDismiss = {},
                onLogin = { selections.add("qr") },
                onRetry = {},
                onPasswordLogin = { selections.add("web") },
                onSmsLogin = { selections.add("sms") },
            )
        }

        compose.onNodeWithText("密码登录（哔哩哔哩官网）").performClick()
        compose.onNodeWithText("短信验证码登录").performClick()
        compose.onNodeWithText("扫码登录").performClick()

        compose.runOnIdle { assertEquals(listOf("web", "sms", "qr"), selections) }
    }
}
