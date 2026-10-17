package com.example.ui.screens

import android.net.Uri
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.model.FloorPlanDocument
import com.example.model.NormalizedPoint
import com.example.model.PlanElementType
import com.example.model.PlanGeometry
import com.example.model.PlanSegment
import com.example.model.RoomType
import com.example.model.RoomZone
import com.example.ui.WifiViewModel
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberOutline
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import java.io.File
import kotlin.math.hypot

private enum class PlanTool(val title: String, val help: String) {
    OUTLINE("Outline", "Tap the outside corners, then Close outline"),
    SKETCH("Pen", "Drag a finger or stylus to sketch wall lines"),
    WALL("Wall", "Tap each end of a wall; repeat for more"),
    DOOR("Door", "Tap both ends of a doorway"),
    WINDOW("Window", "Tap both ends of a window"),
    ROOM("Room", "Tap two opposite room corners, then give it a name"),
    CALIBRATE("Scale", "Tap two points a known distance apart, then enter the tape measurement"),
    ERASE("Erase", "Tap a wall, room, or outline vertex to remove it")
}

@Composable
fun FloorPlanStudio(
    viewModel: WifiViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val initial = remember { viewModel.customPlan.value }
    var draft by remember { mutableStateOf(initial ?: FloorPlanDocument()) }
    var tool by remember { mutableStateOf(PlanTool.OUTLINE) }
    var anchor by remember { mutableStateOf<NormalizedPoint?>(null) }
    var dragLast by remember { mutableStateOf<NormalizedPoint?>(null) }
    var gridEnabled by remember { mutableStateOf(true) }
    var axisSnap by remember { mutableStateOf(false) }
    var photoOpacity by remember { mutableStateOf(.72f) }
    var roomCorners by remember { mutableStateOf<Pair<NormalizedPoint, NormalizedPoint>?>(null) }
    var scaleCorners by remember { mutableStateOf<Pair<NormalizedPoint, NormalizedPoint>?>(null) }
    var roomName by remember { mutableStateOf("") }
    var selectedRoomType by remember { mutableStateOf(RoomType.LIVING_ROOM) }
    var distanceText by remember { mutableStateOf("") }
    var measuredWidth by remember { mutableStateOf("%.2f".format(draft.widthMeters)) }
    var measuredHeight by remember { mutableStateOf("%.2f".format(draft.heightMeters)) }
    var unitsFeet by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var awaitingPhoto by remember { mutableStateOf<Uri?>(null) }
    val history = remember { mutableStateListOf<FloorPlanDocument>() }

    fun update(next: FloorPlanDocument) {
        if (next == draft) return
        if (history.size >= 35) history.removeAt(0)
        history.add(draft)
        draft = next
        errorText = null
    }

    fun photoSelected(uri: Uri) {
        viewModel.importFloorPlanPhoto(uri) { imported, failure ->
            if (imported != null) {
                update(draft.copy(photoFile = imported.first, imageAspect = imported.second, scaleVerified = false))
                anchor = null
                errorText = "New photo loaded. Recalibrate scale and check that old tracings and pins still align."
            } else {
                errorText = "Image import failed: ${failure ?: "unsupported image"}"
            }
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) photoSelected(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = awaitingPhoto
        awaitingPhoto = null
        if (success && uri != null) photoSelected(uri)
        else if (!success) errorText = "Camera cancelled or could not save a photo."
    }

    fun onMapTap(raw: NormalizedPoint) {
        val p = if (gridEnabled) PlanGeometry.snapped(raw) else PlanGeometry.clamp(raw)
        when (tool) {
            PlanTool.OUTLINE -> {
                val vertices = if (draft.outlineClosed) listOf(p) else draft.outline + p
                if (vertices.size <= 1500) update(draft.copy(outline = vertices, outlineClosed = false))
            }
            PlanTool.WALL, PlanTool.DOOR, PlanTool.WINDOW -> {
                val old = anchor
                if (old == null) anchor = p else {
                    val point = if (axisSnap) {
                        if (kotlin.math.abs(p.x - old.x) > kotlin.math.abs(p.y - old.y)) p.copy(y = old.y)
                        else p.copy(x = old.x)
                    } else p
                    if (hypot(old.x - point.x, old.y - point.y) > .008f && draft.segments.size < 2500) {
                        val kind = when (tool) {
                            PlanTool.DOOR -> PlanElementType.DOOR
                            PlanTool.WINDOW -> PlanElementType.WINDOW
                            else -> PlanElementType.WALL
                        }
                        update(draft.copy(segments = draft.segments + PlanSegment(old, point, kind)))
                    }
                    anchor = null
                }
            }
            PlanTool.ROOM -> {
                val old = anchor
                if (old == null) anchor = p else {
                    anchor = null
                    if (kotlin.math.abs(p.x - old.x) > .025f && kotlin.math.abs(p.y - old.y) > .025f)
                        roomCorners = old to p
                    else errorText = "Room is too small. Tap two opposite corners."
                }
            }
            PlanTool.CALIBRATE -> {
                val old = anchor
                if (old == null) anchor = p else {
                    anchor = null
                    distanceText = ""
                    scaleCorners = old to p
                }
            }
            PlanTool.ERASE -> {
                val atRoom = draft.rooms.lastOrNull { r ->
                    p.x in r.leftNorm..(r.leftNorm + r.widthNorm) &&
                        p.y in r.topNorm..(r.topNorm + r.heightNorm)
                }
                val nearLine = draft.segments.indices.minByOrNull { i ->
                    val s = draft.segments[i]
                    val vx = s.end.x - s.start.x
                    val vy = s.end.y - s.start.y
                    val t = (((p.x - s.start.x) * vx + (p.y - s.start.y) * vy) /
                        (vx * vx + vy * vy).coerceAtLeast(.000001f)).coerceIn(0f, 1f)
                    hypot(p.x - s.start.x - t * vx, p.y - s.start.y - t * vy)
                }
                val lineDist = nearLine?.let { i ->
                    val s = draft.segments[i]
                    val vx = s.end.x - s.start.x
                    val vy = s.end.y - s.start.y
                    val t = (((p.x - s.start.x) * vx + (p.y - s.start.y) * vy) /
                        (vx * vx + vy * vy).coerceAtLeast(.000001f)).coerceIn(0f, 1f)
                    hypot(p.x - s.start.x - t * vx, p.y - s.start.y - t * vy)
                } ?: 1f
                val nearVertex = draft.outline.indices.minByOrNull { i ->
                    hypot(p.x - draft.outline[i].x, p.y - draft.outline[i].y)
                }
                if (lineDist < .035f && nearLine != null)
                    update(draft.copy(segments = draft.segments.filterIndexed { i, _ -> i != nearLine }))
                else if (atRoom != null)
                    update(draft.copy(rooms = draft.rooms.filterNot { it.id == atRoom.id }))
                else if (nearVertex != null &&
                    hypot(p.x - draft.outline[nearVertex].x, p.y - draft.outline[nearVertex].y) < .04f)
                    update(draft.copy(
                        outline = draft.outline.filterIndexed { i, _ -> i != nearVertex },
                        outlineClosed = draft.outline.size > 3 && draft.outlineClosed
                    ))
            }
            PlanTool.SKETCH -> Unit
        }
    }

    val photo = remember(draft.photoFile) {
        viewModel.floorPlanPhotoFile(draft.photoFile)?.let { file ->
            runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
        }
    }
    val aspect = (if (photo != null) draft.imageAspect else
        (draft.widthMeters / draft.heightMeters)).coerceIn(.35f, 4f)
    val lineColors = mapOf(
        PlanElementType.WALL to NeonCyan,
        PlanElementType.DOOR to NeonEmerald,
        PlanElementType.WINDOW to Color(0xFFFFCA55)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBackground)
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
            .testTag("floor_plan_studio"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("FLOOR PLAN STUDIO", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                color = NeonCyan, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("Cancel") }
            Button(
                enabled = draft.outline.size >= 3 || draft.photoFile != null ||
                    draft.rooms.isNotEmpty() || draft.segments.isNotEmpty(),
                onClick = {
                    if (viewModel.saveCustomPlan(draft)) onClose()
                    else errorText = "Could not save the map. Check dimensions and retry."
                },
                modifier = Modifier.testTag("save_custom_floorplan")
            ) { Text("Save plan") }
        }
        Text("Sketch or photograph your apartment. The app keeps imported pictures locally; Android system backups depend on your device settings.",
            fontSize = 12.sp, color = Color.LightGray)
        OutlinedTextField(
            value = draft.name,
            onValueChange = { draft = draft.copy(name = it.take(70)) },
            label = { Text("Map name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("map_name")
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { photoPicker.launch("image/*") }, modifier = Modifier.weight(1f)) {
                Text("Import picture")
            }
            Button(onClick = {
                try {
                    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
                    val target = File(dir, "map-${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(
                        context, "${context.packageName}.floorplan.fileprovider", target
                    )
                    awaitingPhoto = uri
                    camera.launch(uri)
                } catch (e: Exception) {
                    errorText = "Camera unavailable: ${e.message ?: "could not create photo"}"
                }
            }, modifier = Modifier.weight(1f)) {
                Text("Take photo")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = draft.outline.isEmpty() && draft.rooms.isEmpty() && draft.segments.isEmpty(),
                onClick = {
                    update(draft.copy(
                        outline = listOf(
                            NormalizedPoint(.04f, .04f), NormalizedPoint(.96f, .04f),
                            NormalizedPoint(.96f, .96f), NormalizedPoint(.04f, .96f)
                        ),
                        outlineClosed = true
                    ))
                }
            ) { Text("Rectangle start") }
            OutlinedButton(enabled = history.isNotEmpty(), onClick = {
                if (history.isNotEmpty()) {
                    draft = history.removeAt(history.lastIndex)
                    anchor = null
                }
            }, modifier = Modifier.testTag("undo_floorplan")) { Text("Undo") }
            OutlinedButton(
                enabled = !draft.outlineClosed && draft.outline.size >= 3,
                onClick = { update(draft.copy(outlineClosed = true)); anchor = null }
            ) { Text("Close outline") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PlanTool.entries) { candidate ->
                FilterChip(
                    selected = tool == candidate,
                    onClick = { tool = candidate; anchor = null; errorText = null },
                    label = { Text(candidate.title) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonCyan,
                        selectedLabelColor = Color.Black
                    ),
                    modifier = Modifier.testTag("plan_tool_${candidate.name.lowercase()}")
                )
            }
        }
        Text(tool.help, fontSize = 12.sp, color = NeonEmerald)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = gridEnabled, onClick = { gridEnabled = !gridEnabled },
                label = { Text("Grid snap") })
            FilterChip(selected = axisSnap, onClick = { axisSnap = !axisSnap },
                label = { Text("Straight lines") })
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val canvasWidth = minOf(maxWidth, 520.dp * aspect)
            val canvasHeight = canvasWidth / aspect
            Box(modifier = Modifier
                .width(canvasWidth)
                .height(canvasHeight)
                .border(1.dp, CyberOutline, RoundedCornerShape(8.dp))
                .background(CyberSurface)
                .testTag("floorplan_drawing_canvas")) {
                if (photo != null) {
                    Image(
                        bitmap = photo, contentDescription = "Imported floor plan reference",
                        contentScale = ContentScale.FillBounds,
                        alpha = photoOpacity,
                        modifier = Modifier.matchParentSize()
                    )
                }
                Canvas(modifier = Modifier
                    .matchParentSize()
                    .pointerInput(tool, gridEnabled, axisSnap) {
                        detectTapGestures { pos ->
                            onMapTap(NormalizedPoint(
                                (pos.x / size.width.toFloat()).coerceIn(0f, 1f),
                                (pos.y / size.height.toFloat()).coerceIn(0f, 1f)
                            ))
                        }
                    }
                    .pointerInput(tool, gridEnabled) {
                        if (tool == PlanTool.SKETCH) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    if (history.size >= 35) history.removeAt(0)
                                    history.add(draft)
                                    dragLast = PlanGeometry.clamp(NormalizedPoint(
                                        pos.x / size.width, pos.y / size.height
                                    ))
                                },
                                onDrag = { change, _ ->
                                    val raw = NormalizedPoint(change.position.x / size.width,
                                        change.position.y / size.height)
                                    val to = if (gridEnabled) PlanGeometry.snapped(raw, .0125f)
                                        else PlanGeometry.clamp(raw)
                                    val from = dragLast
                                    if (from != null &&
                                        hypot(to.x - from.x, to.y - from.y) > .012f &&
                                        draft.segments.size < 2500) {
                                        draft = draft.copy(segments = draft.segments +
                                            PlanSegment(from, to, PlanElementType.WALL))
                                        dragLast = to
                                    }
                                    change.consume()
                                },
                                onDragEnd = { dragLast = null },
                                onDragCancel = { dragLast = null }
                            )
                        }
                    }
                ) {
                    val w = size.width
                    val h = size.height
                    if (gridEnabled) {
                        for (i in 1 until 20) {
                            val fraction = i / 20f
                            drawLine(NeonCyan.copy(alpha = .1f),
                                Offset(fraction * w, 0f), Offset(fraction * w, h), 1f)
                            drawLine(NeonCyan.copy(alpha = .1f),
                                Offset(0f, fraction * h), Offset(w, fraction * h), 1f)
                        }
                    }
                    if (draft.outline.isNotEmpty()) {
                        val path = Path().apply {
                            moveTo(draft.outline[0].x * w, draft.outline[0].y * h)
                            draft.outline.drop(1).forEach { lineTo(it.x * w, it.y * h) }
                            if (draft.outlineClosed) close()
                        }
                        drawPath(path, NeonCyan, style = Stroke(width = 5f))
                        draft.outline.forEach { p ->
                            drawCircle(NeonCyan, 6f, Offset(p.x * w, p.y * h))
                        }
                    }
                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 28f
                        isAntiAlias = true
                        isFakeBoldText = true
                    }
                    draft.rooms.forEach { room ->
                        val left = room.leftNorm * w
                        val top = room.topNorm * h
                        val right = (room.leftNorm + room.widthNorm) * w
                        val bottom = (room.topNorm + room.heightNorm) * h
                        drawRect(NeonEmerald.copy(alpha = .09f), Offset(left, top),
                            androidx.compose.ui.geometry.Size(right - left, bottom - top))
                        drawRect(NeonEmerald.copy(alpha = .7f), Offset(left, top),
                            androidx.compose.ui.geometry.Size(right - left, bottom - top),
                            style = Stroke(width = 2f))
                        drawContext.canvas.nativeCanvas.drawText(room.name, left + 8f,
                            top + 29f, textPaint)
                    }
                    draft.segments.forEach { s ->
                        drawLine(lineColors[s.kind] ?: NeonCyan,
                            Offset(s.start.x * w, s.start.y * h),
                            Offset(s.end.x * w, s.end.y * h),
                            strokeWidth = if (s.kind == PlanElementType.WALL) 6f else 9f)
                    }
                    anchor?.let { drawCircle(NeonAmber, 9f, Offset(it.x * w, it.y * h)) }
                    if (draft.scaleVerified) {
                        drawContext.canvas.nativeCanvas.drawText("CALIBRATED", 14f,
                            h - 14f, textPaint)
                    }
                }
            }
        }

        if (photo != null) {
            Text("Photo opacity", color = Color.LightGray, fontSize = 12.sp)
            Slider(value = photoOpacity, onValueChange = { photoOpacity = it },
                valueRange = .15f..1f)
        }
        Text("Measured apartment dimensions", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = measuredWidth,
                onValueChange = { measuredWidth = it.take(12) },
                label = { Text("Width (m)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).testTag("floorplan_width_m")
            )
            OutlinedTextField(
                value = measuredHeight,
                onValueChange = { measuredHeight = it.take(12) },
                label = { Text("Height (m)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).testTag("floorplan_height_m")
            )
            Button(onClick = {
                val w = measuredWidth.toFloatOrNull()
                val h = measuredHeight.toFloatOrNull()
                if (w != null && h != null && w.isFinite() && h.isFinite() &&
                    w in .5f..500f && h in .5f..500f) {
                    update(draft.copy(widthMeters = w, heightMeters = h, scaleVerified = true))
                } else errorText = "Enter valid positive measured dimensions in meters."
            }, modifier = Modifier.testTag("set_measured_dimensions")) { Text("Apply") }
        }
        Text("Measure the widest horizontal and vertical extent of the actual apartment. Use Scale for a known segment of a traced photo.",
            fontSize = 11.sp, color = Color.LightGray)
        Text(
            "Size: %.2f × %.2f m • %s".format(
                draft.widthMeters, draft.heightMeters,
                if (draft.scaleVerified) "reference calibrated" else "NOT CALIBRATED"
            ),
            fontWeight = FontWeight.Bold,
            color = if (draft.scaleVerified) NeonEmerald else NeonAmber
        )
        Text(
            "Outline ${draft.outline.size} corners • ${draft.segments.size} wall/door/window strokes • ${draft.rooms.size} rooms",
            fontSize = 12.sp, color = Color.LightGray
        )
        if (initial != null && initial.photoFile != draft.photoFile) {
            Text("Picture changed: previous survey pins may no longer line up. Verify or resurvey after saving.",
                color = NeonAmber, fontSize = 12.sp)
        }
        Text(
            "Real map accuracy depends on your tracing and a tape-measured reference. Photos cannot reveal hidden walls or verify dimensions.",
            color = Color.LightGray, fontSize = 12.sp
        )
        errorText?.let { Text(it, color = NeonAmber, fontSize = 12.sp) }
        Button(
            enabled = draft.outline.size >= 3 || draft.photoFile != null ||
                draft.rooms.isNotEmpty() || draft.segments.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (viewModel.saveCustomPlan(draft)) onClose()
                else errorText = "Could not save floor plan"
            }
        ) { Text("Save and start surveying") }
    }

    if (roomCorners != null) {
        val corners = roomCorners!!
        AlertDialog(
            onDismissRequest = { roomCorners = null },
            title = { Text("Name this room") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = roomName,
                        onValueChange = { roomName = it.take(60) },
                        label = { Text("Room label") }, singleLine = true)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(RoomType.entries) { r ->
                            FilterChip(selected = selectedRoomType == r,
                                onClick = { selectedRoomType = r },
                                label = { Text(r.name.replace('_',' ').lowercase().replaceFirstChar { it.uppercase() }) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = roomName.isNotBlank(),
                    onClick = {
                        val a = corners.first
                        val b = corners.second
                        val left = minOf(a.x, b.x)
                        val top = minOf(a.y, b.y)
                        if (draft.rooms.size < 200) update(draft.copy(rooms = draft.rooms +
                            RoomZone("user_room_${System.nanoTime()}", roomName.trim(),
                                left, top, kotlin.math.abs(b.x - a.x),
                                kotlin.math.abs(b.y - a.y), selectedRoomType)))
                        roomName = ""
                        roomCorners = null
                    }
                ) { Text("Add room") }
            },
            dismissButton = { TextButton(onClick = { roomCorners = null }) { Text("Cancel") } }
        )
    }

    if (scaleCorners != null) {
        val selected = scaleCorners!!
        AlertDialog(
            onDismissRequest = { scaleCorners = null },
            title = { Text("Calibrate to a real measurement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Use a tape measure between the two selected points. One correct reference sets both map dimensions.",
                        fontSize = 12.sp)
                    OutlinedTextField(
                        value = distanceText,
                        onValueChange = { distanceText = it.take(12) },
                        label = { Text(if (unitsFeet) "Measured distance (feet)" else "Measured distance (meters)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !unitsFeet, onClick = { unitsFeet = false },
                            label = { Text("Meters") })
                        FilterChip(selected = unitsFeet, onClick = { unitsFeet = true },
                            label = { Text("Feet") })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val typed = distanceText.toFloatOrNull()
                    val meters = if (unitsFeet) (typed ?: 0f) * .3048f else (typed ?: 0f)
                    val calibrated = PlanGeometry.calibrate(draft, selected.first,
                        selected.second, meters)
                    if (calibrated != null) {
                        update(calibrated)
                        measuredWidth = "%.2f".format(calibrated.widthMeters)
                        measuredHeight = "%.2f".format(calibrated.heightMeters)
                        scaleCorners = null
                    } else errorText = "Invalid reference length or points too close together"
                }) { Text("Set accurate scale") }
            },
            dismissButton = { TextButton(onClick = { scaleCorners = null }) { Text("Cancel") } }
        )
    }
}
