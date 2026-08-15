package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Countries
import com.example.domain.model.Country

private val FieldBorder = Color(0xFF26524D)
private val Accent = Color(0xFF5DF2D6)
private val Muted = Color(0xFF98BCB6)
private val DialogBackground = Color(0xFF142021)

/**
 * A country field that looks like the text fields around it but opens a searchable list.
 *
 * A dropdown rather than free text because the value is an ISO code the leaderboard groups on:
 * "Azerbaijan", "Azərbaycan" and "AZ" typed by three players have to end up on the same board.
 */
@Composable
fun CountrySelectorField(
    selected: Country?,
    onSelect: (Country) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Country",
    placeholder: String = "Select your country"
) {
    var pickerOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = label,
            color = Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .border(1.dp, FieldBorder, RoundedCornerShape(4.dp))
                .clickable { pickerOpen = true }
                .padding(horizontal = 16.dp)
        ) {
            if (selected != null) {
                Text(text = selected.flag, fontSize = 20.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = selected.name, color = Color.White, fontSize = 16.sp)
            } else {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = Muted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = placeholder, color = Muted, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.weight(1f))

            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (pickerOpen) {
        CountryPickerDialog(
            selected = selected,
            onDismiss = { pickerOpen = false },
            onSelect = {
                onSelect(it)
                pickerOpen = false
            }
        )
    }
}

/**
 * The country list itself, searchable.
 *
 * Two hundred and fifty rows is too many to scroll through for a player near the end of the
 * alphabet, so the search box filters on both the localised name and the code.
 */
@Composable
fun CountryPickerDialog(
    selected: Country?,
    onDismiss: () -> Unit,
    onSelect: (Country) -> Unit,
    title: String = "Ölkəni seç"
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { Countries.search(query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBackground,
        modifier = Modifier.testTag("country_picker_dialog"),
        title = {
            Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Axtar...", color = Muted) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Muted,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = FieldBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Accent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("country_search_field")
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (results.isEmpty()) {
                    Text(
                        text = "Uyğun ölkə tapılmadı",
                        color = Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                    ) {
                        items(items = results, key = { it.code }) { country ->
                            CountryRow(
                                country = country,
                                isSelected = country.code == selected?.code,
                                onClick = { onSelect(country) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Accent)
            ) {
                Text("BAĞLA")
            }
        }
    )
}

@Composable
private fun CountryRow(
    country: Country,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                color = if (isSelected) Color(0x1A5DF2D6) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag("country_option_${country.code}")
    ) {
        Text(text = country.flag, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = country.name,
            color = if (isSelected) Accent else Color.White,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** The player's country as a compact chip, optionally tappable to change it. */
@Composable
fun CountryChip(
    country: Country?,
    modifier: Modifier = Modifier,
    emptyLabel: String = "Ölkə seçilməyib",
    onClick: (() -> Unit)? = null
) {
    val border = BorderStroke(1.dp, Color(0xFF1B3D3A))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(Color(0xFF0F2624), CircleShape)
            .border(border, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = country?.flag ?: "🏳", fontSize = 15.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = country?.name ?: emptyLabel,
            color = if (country != null) Color.White else Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (onClick != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "Ölkəni dəyiş",
                tint = Muted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
