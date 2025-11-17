package com.tracksure.android.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * An activity for user registration, designed to match the minimal and elegant
 * aesthetic of the LoginActivity.
 */
class SignUpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SignUpScreen(
                    onRegisterClick = {
                        // --- TODO: Implement your registration logic here. ---
                        // This would involve validating all fields and sending them to a server.
                        Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()

                        // On success, navigate back to the Login screen.
                        navigateToLogin()
                    },
                    onBackToLoginClick = {
                        // Navigate back to the login screen without registering.
                        navigateToLogin()
                    }
                )
            }
        }
    }

    private fun navigateToLogin() {
        // Finishes this activity and returns to the previous one (LoginActivity).
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onRegisterClick: () -> Unit,
    onBackToLoginClick: () -> Unit
) {
    // State holders for all the input fields
    var name by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var cnic by remember { mutableStateOf("") }
    var imei by remember { mutableStateOf("") }
    var macAddress by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var retypePassword by remember { mutableStateOf("") }

    val colorScheme = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackToLoginClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Login")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = colorScheme.onSurface
                )
            )
        },
        containerColor = colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                // App Title
                Text(
                    text = "Create Account", // <-- FIX: Changed title text
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                // App Subtitle
                Text(
                    text = "Join the Tracksure Network",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))
            }

            // --- Input Fields ---
            item { SignUpTextField(value = name, onValueChange = { name = it }, label = "Name", icon = Icons.Outlined.Badge) }
            item { SignUpTextField(value = id, onValueChange = { id = it }, label = "ID", icon = Icons.Outlined.Person) }
            item { SignUpTextField(value = email, onValueChange = { email = it }, label = "E-mail", icon = Icons.Outlined.Email, keyboardType = KeyboardType.Email) }
            item { SignUpTextField(value = phone, onValueChange = { phone = it }, label = "Phone Number", icon = Icons.Outlined.Phone, keyboardType = KeyboardType.Phone) }
            item { SignUpTextField(value = cnic, onValueChange = { cnic = it }, label = "CNIC", icon = Icons.Outlined.CreditCard, keyboardType = KeyboardType.Number) }
            item { SignUpTextField(value = imei, onValueChange = { imei = it }, label = "IMEI", icon = Icons.Outlined.PermDeviceInformation, keyboardType = KeyboardType.Number) }
            item { SignUpTextField(value = macAddress, onValueChange = { macAddress = it }, label = "MAC Address", icon = Icons.Outlined.Wifi) }

            item {
                // --- Photo Upload Button ---
                OutlinedButton(
                    onClick = { /* TODO: Implement photo capture/selection logic */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Outlined.CameraAlt, "Photo Icon", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload Photo", fontFamily = FontFamily.Monospace)
                }
            }

            item { SignUpTextField(value = password, onValueChange = { password = it }, label = "Password", icon = Icons.Outlined.Lock, isPassword = true) }
            item { SignUpTextField(value = retypePassword, onValueChange = { retypePassword = it }, label = "Re-type Password", icon = Icons.Outlined.Lock, isPassword = true) }

            item {
                Spacer(modifier = Modifier.height(24.dp))

                // --- Register Button ---
                Button(
                    onClick = onRegisterClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(8.dp),
                    // Basic validation: enable only when all fields are filled and passwords match
                    enabled = listOf(name, id, email, phone, cnic, imei, macAddress, password, retypePassword).all { it.isNotEmpty() } && password == retypePassword
                ) {
                    Text(
                        "Register",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * A reusable styled OutlinedTextField for the signup form.
 */
@Composable
private fun SignUpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        label = { Text(label, fontFamily = FontFamily.Monospace) },
        leadingIcon = { Icon(icon, contentDescription = "$label Icon") },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            focusedLabelColor = MaterialTheme.colorScheme.primary
        )
    )
}


@Preview(showBackground = true, name = "Sign Up Screen Preview")
@Composable
private fun SignUpScreenPreview() {
    MaterialTheme {
        SignUpScreen(onRegisterClick = {}, onBackToLoginClick = {})
    }
}
