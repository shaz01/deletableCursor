import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.util.Log
import java.io.PrintWriter
import java.util.*

class DeletableCursor(private val cursor: Cursor, deletedLines: MutableSet<Int> = mutableSetOf()) :
    Cursor by cursor {
        
    private val deleted: MutableSet<Int> = deletedLines
    private var delegatingList: LinkedList<Int> = initDelegatingList()
    fun delete(pos: Int) {
        val actualPos = actualPosition(pos)
        if (delegatingList.size <= pos) {
            Log.w(TAG, "delete: could not delete $actualPos")
            return
        }
        deleted.add(actualPos)
        delegatingList.removeAt(pos)
    }
    
    fun clearDeletedList() {
        deleted.clear()
        delegatingList = initDelegatingList()
    }

    inline fun <T> of(pos: Int, lambda: DeletableCursor.() -> T): Result<T> {
        return runCatching {
            synchronized(this@DeletableCursor) {
                moveToPosition(pos)
                this.lambda()
            }
        }
    }
    fun forEach(lambda: DeletableCursor.(MutableIterator<Int>) -> Unit) {
        synchronized(this) {
            val iterator = delegatingList.iterator()
            while (iterator.hasNext()) {
                val actualPos = iterator.next()
                moveToActualPosition(actualPos)
                this.lambda(iterator)
            }
        }
    }
    fun removeIf(lambda: DeletableCursor.() -> Boolean) {
        synchronized(this) {
            delegatingList.removeIf {
                val actualPos = it
                cursor.moveToPosition(actualPos)
                this.lambda()
            }
        }
    }

    override fun getCount(): Int {
        return cursor.count - deleted.count()
    }
    override fun moveToPosition(pos: Int): Boolean {
        val actualPos = actualPosition(pos)
        return moveToActualPosition(actualPos)
    }
    private fun moveToActualPosition(actualPos: Int): Boolean{
        return cursor.moveToPosition(actualPos)
    }
    override fun getPosition(): Int {
        return locatePositionBackwards(cursor.position)
    }
    override fun moveToNext(): Boolean {
        return moveToPosition(position + 1)
    }
    override fun move(amount: Int): Boolean {
        return moveToPosition(position + amount)
    }
    override fun moveToLast(): Boolean {
        return moveToPosition(count)
    }
    override fun moveToPrevious(): Boolean {
        return moveToPosition(position - 1)
    }

    fun dumpCurrentRow(out: PrintWriter) {
        val cols = columnNames
        val length = cols.size
        for (i in 0 until length) {
            var value: String = try {
                getString(i)
            } catch (e: SQLiteException) {
                // assume that if the getString threw this exception then the column is not
                // representable by a string, e.g. it is a BLOB.
                "<unprintable>"
            }
            value = value.replace(' ', '≥')
            out.append(value).append(' ')
        }
        out.append("\n")
    }
    
    private fun actualPosition(pos: Int): Int {
        if (pos == -1) return -1
        return delegatingList[pos]
    }
    private fun locatePositionBackwards(pos: Int): Int {
        return delegatingList.indexOf(pos)
    }
    private fun initDelegatingList(): LinkedList<Int> {
        return LinkedList<Int>().also {
            for (i in 0 until cursor.count) {
                it.add(i)
            }
            it.removeIf { actualPos ->
                deleted.contains(actualPos)
            }
        }
    }

    companion object {
        const val TAG = "DeletableCursor"
    }
}
