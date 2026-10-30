package com.schoolerp.pro

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = ErpDatabase.get(this)
        setContent {
            SchoolErpApp(db.dao())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolErpApp(dao: ErpDao) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val profileState by dao.observeProfile().collectAsState(initial = SchoolProfile())
    val profile = profileState ?: SchoolProfile()

    val students by dao.observeStudents().collectAsState(initial = emptyList())
    val payments by dao.observePayments().collectAsState(initial = emptyList())
    val staffList by dao.observeStaff().collectAsState(initial = emptyList())
    val examMarks by dao.observeExamMarks().collectAsState(initial = emptyList())
    val todayDate = remember { SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date()) }
    val todayAttendance by dao.observeAttendanceByDate(todayDate).collectAsState(initial = emptyList())

    var isLoggedIn by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf("DASHBOARD") }

    MaterialTheme(
        colorScheme = lightColorScheme(primary = Color(profile.primaryColorHex))
    ) {
        if (!isLoggedIn) {
            LoginScreen(
                schoolName = profile.schoolName,
                onLoginSuccess = { isLoggedIn = true }
            )
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(profile.schoolName, fontWeight = FontWeight.Bold)
                                Text("Session: ${profile.currentSession}", style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        actions = {
                            IconButton(onClick = { isLoggedIn = false }) {
                                Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = Color.White
                        )
                    )
                },
                bottomBar = {
                    NavigationBar {
                        listOf(
                            Triple("DASHBOARD", "Home", Icons.Default.Dashboard),
                            Triple("STUDENTS", "Students", Icons.Default.People),
                            Triple("FEES", "Fees/PDF", Icons.Default.ReceiptLong),
                            Triple("EXAMS", "Exams/Docs", Icons.Default.School),
                            Triple("SETTINGS", "Settings", Icons.Default.Settings)
                        ).forEach { (route, label, icon) ->
                            NavigationBarItem(
                                selected = currentTab == route,
                                onClick = { currentTab = route },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            ) { padding ->
                Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                    when (currentTab) {
                        "DASHBOARD" -> DashboardModule(
                            students = students,
                            staff = staffList,
                            payments = payments,
                            todayAttendance = todayAttendance,
                            onNavigate = { currentTab = it }
                        )
                        "STUDENTS" -> StudentAndAttendanceModule(
                            students = students,
                            todayDate = todayDate,
                            todayAttendance = todayAttendance,
                            onAddStudent = { s ->
                                scope.launch {
                                    runCatching { dao.insertStudent(s) }
                                        .onSuccess { Toast.makeText(context, "Student Saved!", Toast.LENGTH_SHORT).show() }
                                        .onFailure { Toast.makeText(context, "Duplicate Admission No!", Toast.LENGTH_LONG).show() }
                                }
                            },
                            onMarkAttendance = { rec ->
                                scope.launch { dao.insertAttendance(rec) }
                            }
                        )
                        "FEES" -> FeeAndReceiptModule(
                            context = context,
                            profile = profile,
                            students = students,
                            payments = payments,
                            onCollectFee = { p ->
                                scope.launch {
                                    dao.insertPayment(p)
                                    val file = generatePdfDocument(
                                        context = context,
                                        profile = profile,
                                        docTitle = "OFFICIAL FEE RECEIPT",
                                        fileName = "FeeReceipt_${p.receiptNo}.pdf",
                                        lines = listOf(
                                            "Receipt No: ${p.receiptNo}   |   Date: ${p.paymentDate}",
                                            "Student Name: ${p.studentName}   |   Adm No: ${p.admissionNo}",
                                            "Class: ${p.className}   |   Category: ${p.feeCategory}",
                                            "------------------------------------------------------------",
                                            "Total Fee Amount: Rs. ${p.totalFee}",
                                            "Amount Paid (${p.paymentMode}): Rs. ${p.amountPaid}",
                                            "Remaining Balance Due: Rs. ${p.balanceDue}",
                                            "------------------------------------------------------------",
                                            "Status: ${if (p.balanceDue <= 0.0) "PAID IN FULL" else "PARTIAL PAYMENT"}"
                                        )
                                    )
                                    Toast.makeText(context, "PDF Saved in Downloads: ${file.name}", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                        "EXAMS" -> ExamsAndCertificatesModule(
                            context = context,
                            profile = profile,
                            students = students,
                            examMarks = examMarks,
                            onAddMark = { m -> scope.launch { dao.insertExamMark(m) } }
                        )
                        "SETTINGS" -> SettingsAndStaffModule(
                            profile = profile,
                            staffList = staffList,
                            onSaveProfile = { p ->
                                scope.launch {
                                    dao.saveProfile(p)
                                    Toast.makeText(context, "School Settings Updated!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onAddStaff = { st -> scope.launch { dao.insertStaff(st) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(schoolName: String, onLoginSuccess: () -> Unit) {
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("admin123") }
    var error by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(schoolName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Complete Offline School ERP", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (username.trim() == "admin" && password == "admin123") onLoginSuccess()
                else error = "Default Login: admin / admin123"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Login to ERP")
        }
    }
}

@Composable
fun DashboardModule(
    students: List<Student>,
    staff: List<StaffMember>,
    payments: List<FeePayment>,
    todayAttendance: List<AttendanceRecord>,
    onNavigate: (String) -> Unit
) {
    val totalCollected = payments.sumOf { it.amountPaid }
    val totalPending = payments.sumOf { it.balanceDue }
    val presentToday = todayAttendance.count { it.status == "PRESENT" }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Live School Dashboard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricBox(Modifier.weight(1f), "Students", "${students.size}")
                MetricBox(Modifier.weight(1f), "Staff", "${staff.size}")
                MetricBox(Modifier.weight(1f), "Present Today", "$presentToday")
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricBox(Modifier.weight(1f), "Fee Collected", "Rs. ${totalCollected.toInt()}")
                MetricBox(Modifier.weight(1f), "Pending Balance", "Rs. ${totalPending.toInt()}")
            }
        }
        item {
            Text("Quick ERP Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onNavigate("STUDENTS") }, modifier = Modifier.weight(1f)) { Text("+ Admission") }
                Button(onClick = { onNavigate("FEES") }, modifier = Modifier.weight(1f)) { Text("+ Fee Receipt") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onNavigate("EXAMS") }, modifier = Modifier.weight(1f)) { Text("Admit / Report Card") }
                OutlinedButton(onClick = { onNavigate("SETTINGS") }, modifier = Modifier.weight(1f)) { Text("School Branding") }
            }
        }
        item {
            Text("Recent Fee Transactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(payments.take(8)) { p ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("${p.studentName} (${p.className})", fontWeight = FontWeight.Bold)
                        Text("Receipt: ${p.receiptNo} | ${p.paymentDate}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Rs. ${p.amountPaid.toInt()}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                }
            }
        }
    }
}

@Composable
fun MetricBox(modifier: Modifier = Modifier, title: String, value: String) {
    ElevatedCard(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun StudentAndAttendanceModule(
    students: List<Student>,
    todayDate: String,
    todayAttendance: List<AttendanceRecord>,
    onAddStudent: (Student) -> Unit,
    onMarkAttendance: (AttendanceRecord) -> Unit
) {
    var admNo by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var father by remember { mutableStateOf("") }
    var cls by remember { mutableStateOf("Class 1") }
    var sec by remember { mutableStateOf("A") }
    var roll by remember { mutableStateOf("1") }
    var phone by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("1500") }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("New Student Admission", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(admNo, { admNo = it }, label = { Text("Adm No*") }, modifier = Modifier.weight(1f))
                OutlinedTextField(name, { name = it }, label = { Text("Full Name*") }, modifier = Modifier.weight(1.5f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(father, { father = it }, label = { Text("Father Name") }, modifier = Modifier.weight(1f))
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(cls, { cls = it }, label = { Text("Class") }, modifier = Modifier.weight(1f))
                OutlinedTextField(sec, { sec = it }, label = { Text("Sec") }, modifier = Modifier.weight(0.6f))
                OutlinedTextField(roll, { roll = it }, label = { Text("Roll") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(0.6f))
                OutlinedTextField(fee, { fee = it }, label = { Text("Monthly Fee") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (admNo.isNotBlank() && name.isNotBlank()) {
                        onAddStudent(
                            Student(
                                admissionNo = admNo.trim(),
                                fullName = name.trim(),
                                fatherName = father.trim(),
                                className = cls.trim(),
                                section = sec.trim(),
                                rollNo = roll.toIntOrNull() ?: 1,
                                contactNo = phone.trim(),
                                monthlyFee = fee.toDoubleOrNull() ?: 0.0
                            )
                        )
                        admNo = ""; name = ""; father = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save Student Admission") }
        }

        item {
            Divider(Modifier.padding(vertical = 8.dp))
            Text("Students & Daily Attendance ($todayDate)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        items(students) { s ->
            val currentStatus = todayAttendance.firstOrNull { it.studentId == s.id }?.status ?: "UNMARKED"
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${s.fullName} (Adm: ${s.admissionNo})", fontWeight = FontWeight.Bold)
                    Text("Class: ${s.className}-${s.section} | Roll: ${s.rollNo} | Father: ${s.fatherName} | Status: $currentStatus", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("PRESENT", "ABSENT", "LEAVE").forEach { st ->
                            OutlinedButton(
                                onClick = {
                                    onMarkAttendance(
                                        AttendanceRecord(
                                            studentId = s.id,
                                            studentName = s.fullName,
                                            className = s.className,
                                            dateStr = todayDate,
                                            status = st
                                        )
                                    )
                                }
                            ) { Text(st) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FeeAndReceiptModule(
    context: Context,
    profile: SchoolProfile,
    students: List<Student>,
    payments: List<FeePayment>,
    onCollectFee: (FeePayment) -> Unit
) {
    var selectedStudent by remember { mutableStateOf<Student?>(null) }
    var feeCategory by remember { mutableStateOf("Monthly Tuition Fee") }
    var totalFee by remember { mutableStateOf("1500") }
    var amountPaid by remember { mutableStateOf("1500") }
    var paymentMode by remember { mutableStateOf("Cash") }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Collect Fee & Generate Colo
