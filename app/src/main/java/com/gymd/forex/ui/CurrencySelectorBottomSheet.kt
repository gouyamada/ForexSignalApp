package com.gymd.forex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gymd.forex.domain.CurrencyPair

private val SheetBg = Color(0xFF151B26)
private val ItemBorder = Color(0xFF232B3B)
private val TextPrimary = Color(0xFFF0F4F8)
private val TextSecondary = Color(0xFF8B949E)
private val AccentGreen = Color(0xFF00E676)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencySelectorBottomSheet(
    selectedSymbol: String,
    onSelect: (CurrencyPair) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBg,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0xFF384457))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = "SELECT CURRENCY PAIR",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                items(CurrencyPair.entries) { pair ->
                    val isSelected = pair.symbol == selectedSymbol
                    CurrencyItemCard(
                        pair = pair,
                        isSelected = isSelected,
                        onClick = { onSelect(pair) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrencyItemCard(
    pair: CurrencyPair,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) AccentGreen.copy(alpha = 0.6f) else ItemBorder
    val bgModifier = if (isSelected) {
        Modifier.background(AccentGreen.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
    } else {
        Modifier.background(Color(0xFF0D1117), RoundedCornerShape(12.dp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(bgModifier)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = pair.symbol,
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${pair.displayName} • ${pair.category}",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = AccentGreen,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
