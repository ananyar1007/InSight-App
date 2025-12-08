package com.example.metadatasurvey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MetadataSelectionScreen(onMetadataSelected: (LongArray) -> Unit) {



    val gender = remember { mutableStateOf<Long?>(0) } // 0 = Female, 1 = Male
    val diabetes = remember { mutableStateOf<Long?>(0) } // 0 = No, 1 = Yes
    val hypertension = remember { mutableStateOf<Long?>(1) } // 0 = No, 1 = Yes
    var diabetesDuration by remember { mutableStateOf("0") }
    var age by remember { mutableStateOf("30") }

    // Error states
    var genderError by remember { mutableStateOf(false) }
    var diabetesError by remember { mutableStateOf(false) }
    var durationError by remember { mutableStateOf(false) }
    var ageError by remember { mutableStateOf(false) }
    var hypertensionError by remember { mutableStateOf(false) }

    fun validateAndSubmit() {
        genderError = gender.value == null
        diabetesError = diabetes.value == null
        durationError = diabetesDuration.isEmpty()
        ageError = age.isEmpty()
        hypertensionError = hypertension.value == null

        if (!genderError && !diabetesError && !durationError && !ageError && !hypertensionError) {
            val binnedDiabetesDuration = binDbTime(diabetesDuration.toInt())
            val binnedAge = binAge(age.toInt())

            onMetadataSelected(
                longArrayOf(
                    gender.value!!,
                    diabetes.value!!,
                    hypertension.value!!,
                    binnedDiabetesDuration.toLong(),
                    binnedAge.toLong()
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = Color(0xFF98CCF5)
            ) // Gradient replaces solid color
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        // Title
        Text(
            text = "Enter Your Medical Information",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        )

        // 1. Gender Selection
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "1. What is your gender?",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                color = Color.Black,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                RadioButton(selected = gender.value == 0L, onClick = { gender.value = 0L; genderError = false })
                Text(text = "Female", fontFamily = FontFamily.SansSerif, color = Color.Black)
                Spacer(modifier = Modifier.width(16.dp))
                RadioButton(selected = gender.value == 1L, onClick = { gender.value = 1L; genderError = false })
                Text(text = "Male", fontFamily = FontFamily.SansSerif, color = Color.Black)
            }
            if (genderError) {
                Text("This question is required.", color = Color.Red, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Diabetes Selection
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "2. Have you been diagnosed with Diabetes Mellitus?",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                color = Color.Black,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                RadioButton(selected = diabetes.value == 0L, onClick = { diabetes.value = 0L; diabetesError = false })
                Text(text = "No", fontFamily = FontFamily.SansSerif, color = Color.Black)
                Spacer(modifier = Modifier.width(16.dp))
                RadioButton(selected = diabetes.value == 1L, onClick = { diabetes.value = 1L; diabetesError = false })
                Text(text = "Yes", fontFamily = FontFamily.SansSerif, color = Color.Black)
            }
            if (diabetesError) {
                Text("This question is required.", color = Color.Red, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 3. Diabetes Duration
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "3. How long have you had diabetes in years?",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                color = Color.Black,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Enter zero if you answered No to the previous question.",
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
                fontFamily = FontFamily.SansSerif,
                color = Color.Black,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextField(
                    value = diabetesDuration,
                    onValueChange = {
                        if (it.all { char -> char.isDigit() }) {
                            diabetesDuration = it
                            durationError = false
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(0.8f)
                )
            }
            if (durationError) {
                Text("Please enter a valid duration.", color = Color.Red, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 4. Age Input
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "4. What is your age in years?",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                color = Color.Black,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextField(
                    value = age,
                    onValueChange = {
                        if (it.all { char -> char.isDigit() }) {
                            age = it
                            ageError = false
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(0.8f)
                )
            }
            if (ageError) {
                Text("Please enter your age.", color = Color.Red, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 5. Hypertension Selection
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "5. Have you been diagnosed with hypertension?",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                color = Color.Black,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                RadioButton(selected = hypertension.value == 0L, onClick = { hypertension.value = 0L; hypertensionError = false })
                Text(text = "No", fontFamily = FontFamily.SansSerif, color = Color.Black)
                Spacer(modifier = Modifier.width(16.dp))
                RadioButton(selected = hypertension.value == 1L, onClick = { hypertension.value = 1L; hypertensionError = false })
                Text(text = "Yes", fontFamily = FontFamily.SansSerif, color = Color.Black)
            }
            if (hypertensionError) {
                Text("This question is required.", color = Color.Red, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(48.dp)) // Spacing before the button

        // Next Button
        val customBlue = Color(0xFF12334D)

        Button(
            onClick = { validateAndSubmit() },
            modifier = Modifier.fillMaxWidth(0.5f),
            colors = ButtonDefaults.buttonColors(containerColor = customBlue)
        ) {
            Text(text = "Submit", color = Color.White, fontSize = 20.sp) // Ensures good contrast
        }

        }
    }

