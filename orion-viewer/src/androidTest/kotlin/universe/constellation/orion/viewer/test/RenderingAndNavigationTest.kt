package universe.constellation.orion.viewer.test

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.Canvas
import android.graphics.Point
import android.os.Environment
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import universe.constellation.orion.viewer.*
import universe.constellation.orion.viewer.prefs.GlobalOptions.OPEN_AS_TEMP_BOOK
import universe.constellation.orion.viewer.prefs.GlobalOptions.TEST_SCREEN_HEIGHT
import universe.constellation.orion.viewer.prefs.GlobalOptions.TEST_SCREEN_WIDTH
import universe.constellation.orion.viewer.test.framework.BookDescription
import universe.constellation.orion.viewer.test.framework.InstrumentationTestCase
import java.io.File
import java.io.FileOutputStream
import java.nio.IntBuffer


private val deviceSize = Point(300, 350) //to split page on two screen - page size is 663x886
private const val MANUAL_DEBUG = true

@RunWith(Parameterized::class)
class RenderingAndNavigationTest(private val book: BookDescription) : InstrumentationTestCase(book.toOpenIntent(), additionalParams = {
    intent ->
    intent.putExtra(TEST_SCREEN_WIDTH, deviceSize.x)
    intent.putExtra(TEST_SCREEN_HEIGHT, deviceSize.y)
    intent.putExtra(OPEN_AS_TEMP_BOOK, true)
}) {

   companion object {
        const val SCREENS = 21

        @JvmStatic
        @Parameterized.Parameters(name = "Test simple navigation in {0}")
        fun testData(): Iterable<Array<BookDescription>> {
            return if (MANUAL_DEBUG) {
                arrayOf(arrayOf(BookDescription.entries.first())).asIterable()
            } else {
                BookDescription.entries.map { arrayOf(it) }
            }
        }
    }

    @Volatile
    lateinit var canvas: Canvas
    @Volatile
    lateinit var bitmap: Bitmap

    @Volatile
    lateinit var controller: Controller

    @Test
    fun testProperPages() {
        doTestProperPages()
    }

    private fun doTestProperPages() {
        prepareEngine()

        assertEquals(deviceSize.x, canvas.width)
        assertEquals(deviceSize.y, canvas.height)
        assertEquals(deviceSize.x, bitmap.width)
        assertEquals(deviceSize.y, bitmap.height)

        assertEquals(book.pageCount, controller.pageCount)
        assertEquals(0, controller.currentPage)

        val nextPageList = arrayListOf<IntArray>()
        repeat(SCREENS) {
            lateinit var page: Deferred<PageView?>
            activityScenarioRule.scenario.onActivity { activity ->
                page = controller.drawNext() ?: error("null on ${controller.pageLayoutManager.currentPageLayout()}")
            }
            runBlocking {
                page.await()!!
                flushAndProcessBitmap("next", nextPageList)
            }
        }

        val prevPageList = arrayListOf<IntArray>()
        repeat(SCREENS) {
            lateinit var page: Deferred<PageView?>
            activityScenarioRule.scenario.onActivity {
                page = controller.drawPrev()!!
            }
            runBlocking {
                page.await()!!
                flushAndProcessBitmap("prev", prevPageList)
            }
        }

        assertEquals(nextPageList.size, SCREENS)
        assertEquals(prevPageList.size, SCREENS)

        nextPageList.zipWithNext().forEachIndexed { index, (left, right) ->
            val contentEquals = left.contentEquals(right)
            if (contentEquals) {
                dump(left, right, index, index+1)
            }
            assertFalse(
                "Next screens $index and ${index + 1} are equals: see dump",
                contentEquals
            )
        }

        prevPageList.zipWithNext().forEachIndexed { index, (left, right) ->
            val contentEquals = left.contentEquals(right)
            if (contentEquals) {
                dump(left, right, index, index+1)
            }
            assertFalse(
                "Prev screens $index and ${index + 1} are equals: see dump",
                left.contentEquals(right)
            )
        }

        nextPageList.dropLast(1).reversed().zip(prevPageList.dropLast(1)).forEachIndexed { index, (next, prev) ->
            if (!next.contentEquals(prev)) {
                dump(next, prev, SCREENS - index - 2, index)
            }
            assertArrayEquals("fail on ${SCREENS - index - 2} and ${index}", next, prev)
        }
    }

    private fun flushAndProcessBitmap(
        suffix: String,
        list: ArrayList<IntArray>
    ) {
        activityScenarioRule.scenario.onActivity {
            bitmap.eraseColor(0)
            it.view.draw(canvas)
            processBitmap(list, bitmap)
            if (MANUAL_DEBUG) {
                dump(
                    list.last(),
                    list.size - 1,
                    suffix
                )
            }
        }
    }

    private fun processBitmap(list: MutableList<IntArray>, bitmap: Bitmap) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        list.add(pixels)
    }

    private fun prepareEngine() {
        activityScenarioRule.scenario.onActivity { activity ->
            controller = activity.controller!!
            controller.pageLayoutManager.isSinglePageMode = true
            bitmap = Bitmap.createBitmap(
                activity.view.width,
                activity.view.height,
                Bitmap.Config.ARGB_8888
            )
            canvas = Canvas(bitmap)
        }
    }

    private fun dump(data1: IntArray, data2: IntArray, index1: Int, index2: Int): String {
        dump(data1, index1)
        dump(data2, index2)
        return ""
    }

    private fun dump(data: IntArray, index: Int, suffix: String ="") {
        bitmap.copyPixelsFromBuffer(IntBuffer.wrap(data))
        val file = Environment.getExternalStorageDirectory().path + "/orion/test$suffix${String.format("%02d", index)}.png"
        println("saving dump into $file")
        val file1 = File(file)
        file1.parentFile?.mkdirs()
        file1.createNewFile()
        FileOutputStream(file).use { stream ->
            bitmap.compress(
                CompressFormat.PNG,
                100,
                stream
            )
            stream.close()
        }

    }
}