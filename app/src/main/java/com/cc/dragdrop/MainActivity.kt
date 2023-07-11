package com.cc.dragdrop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cc.dragdrop.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<GraphViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DragDropTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    Ui(viewModel)
                }
            }
        }
    }
}

data class Handlers(
    val onAddShape: (Shape) -> Unit,
    val onToolChange: (ToolType) -> Unit,
    val onHighlightShape: (finger: Offset, size: Float) -> Unit,
    val onDragStart: (finger: Offset, size: Float) -> Unit,
    val onDrag: (Offset) -> Unit,
    val onDragEnd: () -> Unit,
    val onLineStart: (finger: Offset, size: Float) -> Unit,
    val onLineEnd: (finger: Offset, size: Float) -> Unit,
)

@Composable
fun Ui(
    viewModel: GraphViewModel
) {
    val scope = rememberCoroutineScope()
    val selectedTool by viewModel.selectedTool.collectAsState(initial = Square)
    val highlightShapeType by viewModel.highlightShapeType.collectAsState(initial = null)
    val shapes by viewModel.shapes.collectAsState(initial = emptyList())
    val lines by viewModel.lines.collectAsState(initial = emptyList())
    val handlers = remember(viewModel) {
        Handlers(
            onAddShape = { viewModel.add(it) },
            onToolChange = { viewModel.select(it) },
            onDragStart = { finger, size -> viewModel.startDrag(finger, size) },
            onDrag = { offset -> viewModel.drag(offset) },
            onDragEnd = { viewModel.endDrag() },
            onHighlightShape = { finger, size ->
                scope.launch {
                    viewModel.highlightShape(finger, size)
                }
            },
            onLineStart = { finger, size ->
                viewModel.startLine(finger, size)
            },
            onLineEnd = { finger, size ->
                viewModel.endLine(finger, size)
            }
        )
    }
    Graph(
        shapeSizeDp = 36.dp,
        shapeOutlineWidthDp = 3.dp,
        shapeBoxSizeDp = 48.dp,
        shapes = shapes,
        lines = lines,
        highlightShapeType = highlightShapeType,
        selectedTool = selectedTool,
        handlers = handlers,
        modifier = Modifier.fillMaxSize()
    )
}

fun List<Shape>.findWithId(id: String) =
    firstOrNull { it.id == id }

@Composable
fun Graph(
    shapeSizeDp: Dp,
    shapeOutlineWidthDp: Dp,
    shapeBoxSizeDp: Dp,
    shapes: List<Shape>,
    lines: List<Line>,
    highlightShapeType: ShapeType?,
    selectedTool: ToolType,
    handlers: Handlers,
    modifier: Modifier
) {
    with(LocalDensity.current) {
        val dashLengthPx = 6.dp.toPx()
        val dashGapPx = 3.dp.toPx()
        val shapeSizePx = shapeSizeDp.toPx()
        val shapeOutlineWidthPx = shapeOutlineWidthDp.toPx()
        val shapeBoxSizePx = shapeBoxSizeDp.toPx()
        val shapeOffsetPx = (shapeBoxSizePx - shapeSizePx) / 2
        val radius = shapeSizePx / 2

        val shapeSize = remember(shapeSizePx) {
            Size(shapeSizePx, shapeSizePx)
        }
        val shapeBoxSize = remember(shapeBoxSizePx) {
            Size(shapeBoxSizePx, shapeBoxSizePx)
        }
        val halfShapeBoxOffset = remember(shapeBoxSizePx) {
            Offset(shapeBoxSizePx / 2, shapeBoxSizePx / 2)
        }
        val shapeCenter = remember(shapeSizePx) {
            Offset(shapeSizePx / 2, shapeSizePx / 2)
        }
        val outline = remember(shapeOutlineWidthDp) {
            Stroke(shapeOutlineWidthPx)
        }
        val dashedOutline = remember(shapeOutlineWidthDp) {
            Stroke(
                shapeOutlineWidthPx,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLengthPx, dashGapPx), 0f)
            )
        }
        var finger by remember { mutableStateOf(Offset.Zero) }

        fun Shape.getCenter() = offset + halfShapeBoxOffset

        val trianglePath = remember(shapeSizePx) {
            Path().apply {
                moveTo(shapeSizePx / 2, 0f)
                lineTo(shapeSizePx, shapeSizePx)
                lineTo(0f, shapeSizePx)
                close()
            }
        }

        fun DrawScope.drawTriangle(x: Float, y: Float, outlineColor: Color) {
            translate(x + shapeOffsetPx, y + shapeOffsetPx) {
                drawPath(path = trianglePath, color = Color.Red)
                drawPath(path = trianglePath, color = outlineColor, style = outline)
            }
        }

        fun DrawScope.drawSquare(
            x: Float,
            y: Float,
            outlineColor: Color,
            fill: Boolean = true,
            stroke: Stroke = outline
        ) {
            translate(x + shapeOffsetPx, y + shapeOffsetPx) {
                if (fill) {
                    drawRect(
                        color = Color.Blue,
                        topLeft = Offset.Zero,
                        size = shapeSize,
                        style = Fill
                    )
                }
                drawRect(
                    color = outlineColor,
                    topLeft = Offset.Zero,
                    size = shapeSize,
                    style = stroke
                )
            }
        }

        fun DrawScope.drawCircle(x: Float, y: Float, outlineColor: Color) {
            translate(x + shapeOffsetPx, y + shapeOffsetPx) {
                drawCircle(color = Color.Green, center = shapeCenter, radius = radius, style = Fill)
                drawCircle(
                    color = outlineColor,
                    center = shapeCenter,
                    radius = radius,
                    style = outline
                )
            }
        }

        fun DrawScope.drawLine(x: Float, y: Float) {
            val lineY = halfShapeBoxOffset.y + y
            drawLine(
                color = Color.Black,
                strokeWidth = shapeOutlineWidthPx,
                start = Offset(x + shapeOffsetPx, lineY),
                end = Offset(x + shapeBoxSizePx - shapeOffsetPx, lineY)
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.graph)) },
                    actions = {
                        ToolbarButton(
                            toolType = Square,
                            shapeBoxSize = shapeBoxSize,
                            selectedTool = selectedTool,
                            onToolChange = handlers.onToolChange,
                            draw = { x, y -> drawSquare(x, y, Color.Black) }
                        )
                        ToolbarButton(
                            toolType = Circle,
                            shapeBoxSize = shapeBoxSize,
                            selectedTool = selectedTool,
                            onToolChange = handlers.onToolChange,
                            draw = { x, y -> drawCircle(x, y, Color.Black) }
                        )
                        ToolbarButton(
                            toolType = Triangle,
                            shapeBoxSize = shapeBoxSize,
                            selectedTool = selectedTool,
                            onToolChange = handlers.onToolChange,
                            draw = { x, y -> drawTriangle(x, y, Color.Black) }
                        )
                        ToolbarButton(
                            toolType = DrawLine,
                            shapeBoxSize = shapeBoxSize,
                            selectedTool = selectedTool,
                            onToolChange = handlers.onToolChange,
//                            draw = { x, y -> drawLine(x, y, halfShapeBoxOffset, shapeOffsetPx, shapeBoxSizePx,) }
                            draw = { x, y -> drawLine(x, y) }
                        )
                        ToolbarButton(
                            toolType = Select,
                            shapeBoxSize = shapeBoxSize,
                            selectedTool = selectedTool,
                            onToolChange = handlers.onToolChange,
                            draw = { x, y ->
                                drawSquare(
                                    x,
                                    y,
                                    outlineColor = Color.Black,
                                    fill = false,
                                    dashedOutline
                                )
                            }
                        )
                    }
                )
            },
            content = { it ->
                Canvas(
                    modifier = modifier
                        .padding(it)
                        .pointerInput(selectedTool) {
                            when (selectedTool) {
                                is ShapeType -> {
                                    detectTapDragGestures(
                                        onTap = {
                                            handlers.onAddShape(
                                                Shape(
                                                    shapeType = selectedTool,
                                                    offset = it - halfShapeBoxOffset
                                                )
                                            )
                                        },
                                    )
                                }
                                DrawLine -> {
                                    detectTapDragGestures(
                                        onTap = {
                                        },
                                        onDragStart = {
                                            handlers.onLineStart(it, shapeBoxSizePx)
                                        },
                                        onDrag = { change, _ ->
                                            finger = change.position
                                        },
                                        onDragEnd = { handlers.onLineEnd(finger, shapeBoxSizePx) },
                                        onDragCancel = {
                                            handlers.onLineEnd(
                                                finger,
                                                shapeBoxSizePx
                                            )
                                        }
                                    )
                                }
                                Select -> {
                                    detectTapDragGestures(
                                        onTap = { offset ->
                                            handlers.onHighlightShape(offset, shapeBoxSizePx)
                                        },
                                        onDragStart = { offset ->
                                            handlers.onDragStart(offset, shapeBoxSizePx)
                                        },
                                        onDrag = { change, _ ->
                                            handlers.onDrag(change.position)
                                        },
                                        onDragEnd = {
                                            handlers.onDragEnd()
                                        },
                                        onDragCancel = {
                                            handlers.onDragEnd()
                                        }
                                    )
                                }
                            }
                        }
                ) {
                    lines.forEach { line ->
                        drawLine(
                            color = Color.DarkGray, strokeWidth = shapeOutlineWidthPx,
                            start = shapes.findWithId(line.shape1Id)!!.getCenter(),
                            end = line.shape2Id?.let { shapes.findWithId(it)!! }?.getCenter()
                                ?: finger
                        )
                    }
                    shapes.forEach {
                        val outlineColor =
                            if (it.shapeType == highlightShapeType) Color.Magenta else Color.Black
                        when (it.shapeType) {
                            Circle -> drawCircle(it.offset.x, it.offset.y, outlineColor)
                            Square -> drawSquare(it.offset.x, it.offset.y, outlineColor)
                            Triangle -> drawTriangle(it.offset.x, it.offset.y, outlineColor)
                        }
                    }
                }
            }
        )
    }
}

// one way to work around the compose compiler bug
//fun DrawScope.drawLine(x: Float, y: Float, halfShapeBoxOffset: Offset, shapeOffsetPx: Float, shapeBoxSizePx: Float) {
//    translate(x, y) {
//        val lineY = halfShapeBoxOffset.y
//        drawLine(color = Color.Black, start = Offset(shapeOffsetPx, lineY), end = Offset(shapeBoxSizePx - shapeOffsetPx, lineY))
//    }
//}

@Composable
fun ToolbarButton(
    toolType: ToolType,
    shapeBoxSize: Size,
    selectedTool: ToolType,
    onToolChange: (ToolType) -> Unit,
    draw: DrawScope.(Float, Float) -> Unit
) {
    IconButton(onClick = { onToolChange(toolType) }) {
        Canvas(modifier = Modifier.size(48.dp)) {
            if (selectedTool == toolType) {
                drawRect(color = Color.White, topLeft = Offset.Zero, size = shapeBoxSize)
            }
            draw(0f, 0f)
        }
    }
}


// Gesture detector for taps and drags
// copied from DragGestureDetector.kt in Jetpack Compose
// LICENSE: Apache 2.0
/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
suspend fun PointerInputScope.detectTapDragGestures(
    onTap: (Offset) -> Unit = { },
    onDragStart: (Offset) -> Unit = { },
    onDragEnd: () -> Unit = { },
    onDragCancel: () -> Unit = { },
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit = { _, _ -> }
) {
    forEachGesture {
        awaitPointerEventScope {
            val down = awaitFirstDown(requireUnconsumed = false)
            var drag: PointerInputChange?
            var overSlop = Offset.Zero
            do {
                drag = awaitTouchSlopOrCancellation(down.id) { change, over ->
                    change.consumePositionChange()
                    overSlop = over
                }
            } while (drag != null && !drag.positionChangeConsumed())
            if (drag != null) {
                onDragStart.invoke(down.position) // CHANGED TO down instead of drag
                onDrag(drag, overSlop)
                if (
                    !drag(drag.id) {
                        onDrag(it, it.positionChange())
                        it.consumePositionChange()
                    }
                ) {
                    onDragCancel()
                } else {
                    onDragEnd()
                }
            } else {                    // ADDED
                onTap(down.position)    // ADDED
            }
        }
    }
}
