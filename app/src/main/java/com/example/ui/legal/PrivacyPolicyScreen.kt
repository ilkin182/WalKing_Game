package com.example.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.util.LocalWindowWidthSizeClass

/**
 * Placeholder Privacy Policy content, editable in-app. The Play Console listing also requires a
 * *hosted* privacy policy URL (see the manual Play Console checklist) — this screen alone does
 * not satisfy that requirement, but gives users an in-app reference and a starting point to adapt.
 */
@Composable
fun PrivacyPolicyScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCompactWidth = LocalWindowWidthSizeClass.current == WindowWidthSizeClass.Compact

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1A1B))
            .testTag("privacy_policy_screen_container")
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .background(Color(0x33FFFFFF), CircleShape)
                        .size(48.dp)
                        .testTag("close_privacy_policy_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(0.dp))
                Text(
                    text = "GİZLİLİK SİYASƏTİ",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (isCompactWidth) Modifier.fillMaxWidth() else Modifier.width(720.dp))
                    .align(if (isCompactWidth) Alignment.Start else Alignment.CenterHorizontally)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Son yenilənmə: [TARİX DAXİL EDİN]",
                    color = Color(0xFF98BCB6),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                PolicySection(
                    title = "Topladığımız məlumatlar",
                    body = "• Hesab məlumatları: qeydiyyat zamanı verdiyiniz e-poçt ünvanı (Firebase " +
                        "Authentication vasitəsilə).\n" +
                        "• Məkan məlumatları: tətbiqin əsas funksiyası — GPS-lə yeriş izləmə və " +
                        "hüceyrə fəthi — üçün cari məkanınız, yalnız tətbiq aktiv istifadə " +
                        "olunarkən.\n" +
                        "• Fəaliyyət məlumatları: qət etdiyiniz məsafə, addım sayı, fəth edilmiş " +
                        "ərazilər — cihazınızda yerli olaraq saxlanılır."
                )
                PolicySection(
                    title = "Məlumatlardan necə istifadə edirik",
                    body = "Topladığımız məlumatlar yalnız tətbiqin əsas funksionallığını " +
                        "(hesab girişi, xəritə üzərində irəliləyişinizin göstərilməsi) təmin " +
                        "etmək üçün istifadə olunur. Məlumatlarınızı üçüncü tərəflərə satmırıq."
                )
                PolicySection(
                    title = "Məlumatların saxlanması",
                    body = "Fəth edilmiş ərazilər və statistika cihazınızda yerli verilənlər " +
                        "bazasında saxlanılır. Hesab məlumatlarınız Firebase Authentication " +
                        "vasitəsilə təhlükəsiz şəkildə idarə olunur."
                )
                PolicySection(
                    title = "Hesabınızı silmək",
                    body = "Hesabınızı və ona bağlı məlumatları silmək üçün bizimlə " +
                        "[DƏSTƏK E-POÇTU DAXİL EDİN] ünvanı üzərindən əlaqə saxlayın."
                )
                PolicySection(
                    title = "Bizimlə əlaqə",
                    body = "Bu Gizlilik Siyasəti ilə bağlı suallarınız varsa, " +
                        "[DƏSTƏK E-POÇTU DAXİL EDİN] ünvanına yazın."
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PolicySection(title: String, body: String) {
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Text(
            text = title,
            color = Color(0xFF5DF2D6),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            text = body,
            color = Color(0xFFE2EFEA),
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}
