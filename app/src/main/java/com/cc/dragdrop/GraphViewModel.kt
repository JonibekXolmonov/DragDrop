package com.cc.dragdrop

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import com.cc.dragdrop.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

class GraphViewModel: ViewModel() {
    private val _highlightShapeType = MutableStateFlow<ShapeType?>(null)
    val highlightShapeType: Flow<ShapeType?>
        get() = _highlightShapeType

    private val _selectedTool = MutableStateFlow<ToolType>(Square)
    val selectedTool: Flow<ToolType>
        get() = _selectedTool

    private val _shapes = MutableStateFlow<List<Shape>>(emptyList())
    val shapes: Flow<List<Shape>>
        get() = _shapes

    private val _lines = MutableStateFlow<List<Line>>(emptyList())
    val lines: Flow<List<Line>>
        get() = _lines

    fun select(tool: ToolType) {
        _selectedTool.value = tool
    }

    suspend fun highlightShape(finger: Offset, size: Float) = withContext(Dispatchers.Default) {
        _shapes.value.findAt(finger, size)?.let { shape ->
            repeat(3) {
                _highlightShapeType.value = shape.shapeType
                delay(200)
                _highlightShapeType.value = null
                if (it != 2) {
                    delay(200)
                }
            }
        }
    }

    fun add(shape: Shape) {
        _shapes.value = _shapes.value + shape
    }

    private var dragShape: Shape? = null
    private var dragShapeOffset: Offset = Offset.Zero

    fun startDrag(finger: Offset, size: Float) {
        dragShape = _shapes.value.findAt(finger, size)?.apply {
            dragShapeOffset = finger - offset
        }
    }

    fun drag(offset: Offset) {
        dragShape?.let { shape ->
            val newShape = shape.copy(offset = offset - dragShapeOffset)
            _shapes.value = _shapes.value - shape + newShape
            dragShape = newShape
        }
    }

    fun endDrag() {
        dragShape = null
    }

    private var lineInProgress: Line? = null
    fun startLine(finger: Offset, size: Float) {
        _shapes.value.findAt(finger, size)?.let { shape ->
            lineInProgress = Line(shape.id).apply {
                _lines.value = _lines.value + this
            }
        }
    }

    fun endLine(finger: Offset, size: Float) {
        lineInProgress?.let { line ->
            _shapes.value.findAt(finger, size)?.let { endShape ->
                _lines.value = _lines.value - line + line.copy(shape2Id = endShape.id)
            } ?: run {
                _lines.value = _lines.value - line
            }
            lineInProgress = null
        }
    }

    private fun List<Shape>.findAt(offset: Offset, shapeBoxSizePx: Float) =
        reversed().find { shape ->
            val normalized = offset - shape.offset
            normalized.x >= 0 &&
                    normalized.y >= 0 &&
                    normalized.x <= shapeBoxSizePx &&
                    normalized.y <= shapeBoxSizePx
        }
}
