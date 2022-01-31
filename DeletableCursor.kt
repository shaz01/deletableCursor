package com.olcayaras.photodeleterrebuild

import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.util.Log

class DeletableCursor(private val cursor: Cursor) : Cursor by cursor {
    private val deleted: MutableSet<Int> = mutableSetOf()
    fun delete(pos: Int){
        if (cursor.moveToPosition(pos)){
            val deleted = deleted.add(locatePosition(pos))
        }
    }
    fun <T> of(pos: Int, lambda: RemovableCursor.() -> T): Result<T> {
        return runCatching {
            moveToPosition(pos)
            this.lambda()
        }
    }

    override fun getCount(): Int {
        return cursor.count - deleted.count()
    }

    override fun moveToPosition(pos: Int): Boolean {
        val actualLocation = locatePosition(pos)
        return cursor.moveToPosition(actualLocation)
    }
    override fun getPosition(): Int {
        return locatePositionBackwards(cursor.position)
    }
    override fun moveToNext(): Boolean {
        return moveToPosition(position + 1)
    }
    override fun move(amount: Int): Boolean {
        return moveToPosition(position+amount)
    }
    override fun moveToLast(): Boolean {
        return moveToPosition(count)
    }
    override fun moveToPrevious(): Boolean {
        return moveToPosition(position-1)
    }
    override fun moveToFirst(): Boolean{
        return moveToPosition(0) 
    }

    private fun locatePosition(pos: Int): Int {
        val filtered: MutableList<Int> = mutableListOf<Int>().also { list ->
            list.addAll(deleted.filter { it <= pos })
        }
        val position = pos + filtered.size
        return if (deleted.contains(position)){
            filtered.add(position)
            locatePositionNoCheck(pos)
        } else {
            position
        }
    }
    private fun locatePositionNoCheck(pos: Int): Int{
        if (deleted.contains(pos)){
            return locatePositionNoCheck(pos+1)
        }
        return pos
    }
    private fun locatePositionBackwards(pos: Int): Int{
        val filtered: MutableList<Int> = mutableListOf<Int>().also { list ->
            list.addAll(deleted.filter { it <= pos })
        }
        return locatePositionBackwardsNoCheck(pos - filtered.size)
    }
    private fun locatePositionBackwardsNoCheck(pos: Int): Int{
        if (deleted.contains(pos)){
            return locatePositionBackwardsNoCheck(pos+1)
        }
        return pos
    }
}
