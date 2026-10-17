package qisma.app

import androidx.room.*

@Entity(tableName = "projects")
data class ProjectEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val updatedAt: Long, val json: String)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC") suspend fun all(): List<ProjectEntity>
    @Insert suspend fun insert(p: ProjectEntity): Long
    @Update suspend fun update(p: ProjectEntity)
    @Query("DELETE FROM projects WHERE id = :id") suspend fun delete(id: Long)
}

@Database(entities = [ProjectEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() { abstract fun dao(): ProjectDao }
