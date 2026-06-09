package com.example

import com.example.ui.DocFormatRepository
import com.example.ui.DocFormatSpan
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RibbonActionTest {
    @Test
    fun testDocFormatRepositorySpansSplitting() {
        val docId = 1
        
        // Add manual spans
        val list = DocFormatRepository.getSpans(docId)
        list.clear() // Ensure empty
        list.add(DocFormatSpan(0, 10, "bold"))
        
        // Remove overlap
        DocFormatRepository.removeSpansRange(docId, 2, 5)
        
        // Expected: Split (0,10) -> (0,2) and (5,10)
        assertEquals(2, list.size)
        
        val sortedList = list.sortedBy { it.start }
        assertEquals(0, sortedList[0].start)
        assertEquals(2, sortedList[0].end)
        
        assertEquals(5, sortedList[1].start)
        assertEquals(10, sortedList[1].end)
    }
}
