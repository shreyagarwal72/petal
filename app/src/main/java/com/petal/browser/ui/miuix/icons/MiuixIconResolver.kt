package com.petal.browser.ui.miuix.icons

import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.AddCircle
import top.yukonga.miuix.kmp.icon.extended.Alarm
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Cut
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Email
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Merge
import top.yukonga.miuix.kmp.icon.extended.Mic
import top.yukonga.miuix.kmp.icon.extended.MicSlash
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Paste
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Phone
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Redo
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Remove
import top.yukonga.miuix.kmp.icon.extended.Rename
import top.yukonga.miuix.kmp.icon.extended.Reply
import top.yukonga.miuix.kmp.icon.extended.ReplyAll
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Translate
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.icon.extended.Unlock
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.icon.extended.VolumeOff
import top.yukonga.miuix.kmp.icon.extended.VolumeUp
import top.yukonga.miuix.kmp.icon.extended.ZoomOut

/**
 * Central icon resolver for Miuix mode. Maps a Material [ImageVector] (by its name, e.g.
 * "Rounded.Home", "AutoMirrored.Rounded.ArrowBack") to the matching icon from miuix-icons.
 * Returns the original vector when the library has no equivalent, so nothing ever disappears.
 * Used by the drop-in Icon wrapper in ui/miuix/wrappers; call sites only change their import.
 */
object MiuixIconResolver {
    fun resolve(vector: ImageVector): ImageVector {
        val base = vector.name.substringAfterLast('.')
        return lookup(base) ?: vector
    }

    private fun lookup(base: String): ImageVector? = when (base) {
        "Check", "Done" -> MiuixIcons.Basic.Check
        "Add", "AddBox" -> MiuixIcons.Regular.Add
        "AddCircle", "AddCircleOutline" -> MiuixIcons.Regular.AddCircle
        "Alarm", "AccessAlarm" -> MiuixIcons.Regular.Alarm
        "Album" -> MiuixIcons.Regular.Album
        "ArrowBack", "ArrowBackIosNew", "ArrowBackIos" -> MiuixIcons.Regular.Back
        "Backup", "CloudUpload" -> MiuixIcons.Regular.Backup
        "ChevronLeft", "KeyboardArrowLeft", "NavigateBefore" -> MiuixIcons.Regular.ChevronBackward
        "ChevronRight", "KeyboardArrowRight", "ArrowForwardIos", "NavigateNext" -> MiuixIcons.Regular.ChevronForward
        "Clear" -> MiuixIcons.Regular.Clear
        "Close" -> MiuixIcons.Regular.Close
        "Cloud", "CloudQueue" -> MiuixIcons.Regular.CloudFill
        "Contacts" -> MiuixIcons.Regular.Contacts
        "ContentCopy", "FileCopy" -> MiuixIcons.Regular.Copy
        "ContentCut" -> MiuixIcons.Regular.Cut
        "Delete", "DeleteOutline", "DeleteForever" -> MiuixIcons.Regular.Delete
        "Download", "FileDownload", "SaveAlt" -> MiuixIcons.Regular.Download
        "Edit", "Create" -> MiuixIcons.Regular.Edit
        "Email", "Mail" -> MiuixIcons.Regular.Email
        "ExpandLess", "KeyboardArrowUp" -> MiuixIcons.Regular.ExpandLess
        "ExpandMore", "KeyboardArrowDown" -> MiuixIcons.Regular.ExpandMore
        "InsertDriveFile", "Description" -> MiuixIcons.Regular.File
        "FilterList" -> MiuixIcons.Regular.Filter
        "FolderOpen" -> MiuixIcons.Regular.Folder
        "Folder" -> MiuixIcons.Regular.FolderFill
        "Forward" -> MiuixIcons.Regular.Forward
        "GridView", "Apps" -> MiuixIcons.Regular.GridView
        "Help", "HelpOutline" -> MiuixIcons.Regular.Help
        "VisibilityOff" -> MiuixIcons.Regular.Hide
        "Home" -> MiuixIcons.Regular.Home
        "Image", "Photo" -> MiuixIcons.Regular.Image
        "FileUpload" -> MiuixIcons.Regular.Import
        "Info", "InfoOutline" -> MiuixIcons.Regular.Info
        "Layers" -> MiuixIcons.Regular.Layers
        "Link" -> MiuixIcons.Regular.Link
        "List", "ViewList" -> MiuixIcons.Regular.ListView
        "LocationOn", "Place" -> MiuixIcons.Regular.Location
        "Lock" -> MiuixIcons.Regular.Lock
        "MergeType" -> MiuixIcons.Regular.Merge
        "Mic" -> MiuixIcons.Regular.Mic
        "MicOff" -> MiuixIcons.Regular.MicSlash
        "MoreVert", "MoreHoriz" -> MiuixIcons.Regular.More
        "MusicNote" -> MiuixIcons.Regular.Music
        "Notes" -> MiuixIcons.Regular.Notes
        "ContentPaste" -> MiuixIcons.Regular.Paste
        "Pause" -> MiuixIcons.Regular.Pause
        "Phone" -> MiuixIcons.Regular.Phone
        "PushPin" -> MiuixIcons.Regular.Pin
        "PlayArrow" -> MiuixIcons.Regular.Play
        "QueueMusic", "PlaylistPlay" -> MiuixIcons.Regular.Playlist
        "History" -> MiuixIcons.Regular.Recent
        "Redo" -> MiuixIcons.Regular.Redo
        "Refresh", "Sync" -> MiuixIcons.Regular.Refresh
        "Remove" -> MiuixIcons.Regular.Remove
        "DriveFileRenameOutline" -> MiuixIcons.Regular.Rename
        "Reply" -> MiuixIcons.Regular.Reply
        "ReplyAll" -> MiuixIcons.Regular.ReplyAll
        "Report" -> MiuixIcons.Regular.Report
        "QrCodeScanner", "DocumentScanner" -> MiuixIcons.Regular.Scan
        "Search" -> MiuixIcons.Regular.Search
        "SelectAll" -> MiuixIcons.Regular.SelectAll
        "Send" -> MiuixIcons.Regular.Send
        "Settings" -> MiuixIcons.Regular.Settings
        "Share" -> MiuixIcons.Regular.Share
        "Visibility" -> MiuixIcons.Regular.Show
        "Sort" -> MiuixIcons.Regular.Sort
        "Store" -> MiuixIcons.Regular.Store
        "Checklist" -> MiuixIcons.Regular.Tasks
        "Palette", "ColorLens" -> MiuixIcons.Regular.Theme
        "Timer" -> MiuixIcons.Regular.Timer
        "Translate" -> MiuixIcons.Regular.Translate
        "Tune" -> MiuixIcons.Regular.Tune
        "Undo" -> MiuixIcons.Regular.Undo
        "LockOpen" -> MiuixIcons.Regular.Unlock
        "Update" -> MiuixIcons.Regular.Update
        "VolumeOff" -> MiuixIcons.Regular.VolumeOff
        "VolumeUp" -> MiuixIcons.Regular.VolumeUp
        "ZoomOut" -> MiuixIcons.Regular.ZoomOut
        else -> null
    }
}
