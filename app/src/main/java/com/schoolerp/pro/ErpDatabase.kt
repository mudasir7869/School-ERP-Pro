package com.schoolerp.pro

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "school_profile")
data class SchoolProfile(
    @PrimaryKey val id: Int = 1,
    val schoolName: String = "My School ERP Pro",
    val address: String = "Main Campus Road",
    val phone: String = "+91-9000000000",
    val principalName: String = "Principal",
    val currentSession: String = "2026-2027",
    val primaryColorHex: Long = 0xFF1565C0
)

@Entity(
    tableName = "students",
    indices = [Index(value = ["admissionNo"], unique = true)]
)
data class Student(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val admissionNo: String,
    val fullName: String,
    val fatherName: String,
    val className: String,
    val section: String,
    val rollNo: Int,
    val contactNo: String,
    val monthlyFee: Double,
    val session: String = "2026-2027"
)

@Entity(tableName = "fee_payments")
data class FeePayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptNo: String,
    val studentId: Long,
    val studentName: String,
    val admissionNo: String,
    val className: String,
    val feeCategory: String,
    val totalFee: Double,
    val amountPaid: Double,
    val balanceDue: Double,
    val paymentDate: String,
    val paymentMode: String
)

@Entity(tableName = "staff")
data class StaffMember(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val staffCode: String,
    val fullName: String,
    val designation: String,
    val isTeaching: Boolean,
    val phone: String,
    val salary: Double
)

@Entity(tableName = "attendance")
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val studentName: String,
    val className: String,
    val dateStr: String,
    val status: String // PRESENT, ABSENT, LEAVE
)

@Entity(tableName = "exam_marks")
data class ExamMark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val examName: String,
    val studentId: Long,
    val studentName: String,
    val admissionNo: String,
    val className: String,
    val subjectName: String,
    val maxMarks: Double,
    val marksObtained: Double
)

@Dao
interface ErpDao {
    @Query("SELECT * FROM school_profile WHERE id = 1")
    fun observeProfile(): Flow<SchoolProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: SchoolProfile)

    @Query("SELECT * FROM students ORDER BY className, rollNo ASC")
    fun observeStudents(): Flow<List<Student>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStudent(student: Student)

    @Delete
    suspend fun deleteStudent(student: Student)

    @Query("SELECT * FROM fee_payments ORDER BY id DESC")
    fun observePayments(): Flow<List<FeePayment>>

    @Insert
    suspend fun insertPayment(payment: FeePayment)

    @Query("SELECT * FROM staff ORDER BY fullName ASC")
    fun observeStaff(): Flow<List<StaffMember>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStaff(staff: StaffMember)

    @Query("SELECT * FROM attendance WHERE dateStr = :dateStr")
    fun observeAttendanceByDate(dateStr: String): Flow<List<AttendanceRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(record: AttendanceRecord)

    @Query("SELECT * FROM exam_marks ORDER BY id DESC")
    fun observeExamMarks(): Flow<List<ExamMark>>

    @Insert
    suspend fun insertExamMark(mark: ExamMark)
}

@Database(
    entities = [
        SchoolProfile::class,
        Student::class,
        FeePayment::class,
        StaffMember::class,
        AttendanceRecord::class,
        ExamMark::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ErpDatabase : RoomDatabase() {
    abstract fun dao(): ErpDao

    companion object {
        @Volatile
        private var INSTANCE: ErpDatabase? = null

        fun get(context: Context): ErpDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    ErpDatabase::class.java,
                    "school_erp_pro.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
