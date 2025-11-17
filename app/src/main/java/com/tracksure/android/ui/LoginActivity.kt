package com.tracksure.android.ui

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tracksure.android.MainActivity

class LoginActivity : ComponentActivity() {

    // Activity result launcher for handling the Bluetooth enable request.
    private val bluetoothActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // User successfully enabled Bluetooth.
            navigateToMainApp()
        } else {
            // User did not enable Bluetooth, which is required for the app to function.
            Toast.makeText(this, "Bluetooth is required to use Tracksure.", Toast.LENGTH_LONG).show()
        }
    }

    // Activity result launcher for handling the result from SignUpActivity.
    private val signUpLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // This block is called when SignUpActivity finishes.
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Registration successful. Please log in.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                LoginScreen(
                    onLoginClick = { id, password ->
                        // --- TODO: Replace this with your actual authentication logic. ---
                        if (authenticateUser(id, password)) {
                            checkBluetoothAndProceed()
                        } else {
                            Toast.makeText(this, "Invalid credentials", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onForgotPasswordClick = {
                        // --- TODO: Implement your "forgot password" flow here. ---
                        Toast.makeText(this, "Forgot Password clicked", Toast.LENGTH_SHORT).show()
                    },
                    onSignUpClick = {
                        val intent = Intent(this, SignUpActivity::class.java)
                        signUpLauncher.launch(intent)
                    }
                )
            }
        }
    }

    private fun authenticateUser(userId: String, pass: String): Boolean {
        // For now, any non-empty input is considered valid for demonstration purposes.
        return userId.isNotBlank() && pass.isNotBlank()
    }

    private fun checkBluetoothAndProceed() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_LONG).show()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            // Bluetooth is off; create an intent to request the user to enable it.
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothActivityResultLauncher.launch(enableBtIntent)
        } else {
            // Bluetooth is already on; proceed directly to the main application.
            navigateToMainApp()
        }
    }

    private fun navigateToMainApp() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}


@Composable
fun LoginScreen(
    onLoginClick: (String, String) -> Unit,
    onForgotPasswordClick: () -> Unit,
    onSignUpClick: () -> Unit // <-- Parameter for the sign-up action
) {
    // State holders for the input fields.
    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding() // Ensure content is not obscured by the status bar.
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Title - styled to match the aesthetic in AboutSheet.kt.
            Text(
                text = "Tracksure",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            // App Subtitle.
            Text(
                text = "Decentralized Mesh Messaging",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            // User ID Input Field.
            OutlinedTextField(
                value = userId,
                onValueChange = { userId = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("User ID", fontFamily = FontFamily.Monospace) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = "User ID Icon"
                    )
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorScheme.primary,
                    focusedLabelColor = colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password Input Field.
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password", fontFamily = FontFamily.Monospace) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Password Icon"
                    )
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorScheme.primary,
                    focusedLabelColor = colorScheme.primary
                )
            )

            // Forgot Password Text Button.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = "forgot password?",
                    modifier = Modifier
                        .clickable { onForgotPasswordClick() }
                        .padding(vertical = 8.dp),
                    color = colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Login Button.
            Button(
                onClick = { onLoginClick(userId, password) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "Login",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // --- NEW REGISTER BUTTON ---
            Spacer(modifier = Modifier.height(12.dp))

            // A secondary button for registration, styled consistently
            OutlinedButton(
                onClick = onSignUpClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    "Register",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Login Screen Preview")
@Composable
private fun LoginScreenPreview() {
    MaterialTheme {
        LoginScreen(
            onLoginClick = { _, _ -> },
            onForgotPasswordClick = {},
            onSignUpClick = {}
        )
    }
}
