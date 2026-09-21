# uitest.ps1 - programove ovladani GUI pro overeni (bez klikani): nastavi filtr, vybere radek, zachyti okno
param([string]$File, [string]$Shot = "shot4.png", [string]$Search = "", [int]$Filter = 0, [int]$SelectRow = -1, [int]$Scale = -1, [int]$HoverX = -1, [int]$HoverY = -1, [int]$Tab = -1, [int]$Cmd = -1)
Add-Type -AssemblyName System.Drawing
Add-Type @'
using System; using System.Text; using System.Runtime.InteropServices;
public class UI {
  [DllImport("user32.dll", SetLastError=true)] public static extern bool GetWindowRect(IntPtr h, out R r);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetClassName(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr dc, uint f);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern IntPtr SendMessage(IntPtr h, uint m, IntPtr w, IntPtr l);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern IntPtr SendMessage(IntPtr h, uint m, IntPtr w, string l);
  [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint m, IntPtr w, IntPtr l);
  [DllImport("user32.dll")] public static extern IntPtr GetDlgItem(IntPtr h, int id);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern IntPtr FindWindow(string cls, string title);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern IntPtr FindWindowEx(IntPtr parent, IntPtr after, string cls, string title);
  [DllImport("user32.dll")] public static extern IntPtr GetWindow(IntPtr h, uint cmd);
  [DllImport("user32.dll")] public static extern bool GetCursorPos(out P p);
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [StructLayout(LayoutKind.Sequential)] public struct P { public int X, Y; }
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern IntPtr SendMessage(IntPtr h, uint m, IntPtr w, StringBuilder l);
  [StructLayout(LayoutKind.Sequential)] public struct R { public int L, T, Rt, B; }
}
'@
$outDir = Join-Path $PSScriptRoot "..\out"; $exe = Join-Path $outDir "mppview.exe"
Get-Process mppview -ErrorAction SilentlyContinue | Stop-Process -Force
$env:MPPVIEW_TEST = "1"
Start-Process -FilePath $exe -ArgumentList "`"$File`""
$h = [IntPtr]::Zero
for ($i = 0; $i -lt 40 -and $h -eq [IntPtr]::Zero; $i++) { Start-Sleep -Milliseconds 250; $h = [UI]::FindWindow("MppViewMain", [NullString]::Value) }
Start-Sleep -Milliseconds 800
$cn = New-Object System.Text.StringBuilder 64; [UI]::GetClassName($h, $cn, 64) | Out-Null
$rr = New-Object UI+R; $okr = [UI]::GetWindowRect($h, [ref]$rr)
"hwnd=$h class=$($cn.ToString()) rect=$okr $($rr.L),$($rr.T),$($rr.Rt),$($rr.B) err=$([Runtime.InteropServices.Marshal]::GetLastWin32Error()) dlg905=$([UI]::GetDlgItem($h, 905)) dlg1=$([UI]::GetDlgItem($h, 1))"
[UI]::SetForegroundWindow($h) | Out-Null
$WM_SETTEXT = 0x000C; $WM_COMMAND = 0x0111; $CB_SETCURSEL = 0x014E; $LVM_SETITEMSTATE = 0x102B
if ($Search -ne "") { $e = [UI]::GetDlgItem($h, 901); [UI]::SendMessage($e, $WM_SETTEXT, [IntPtr]::Zero, $Search) | Out-Null }
if ($Filter -gt 0) { $c = [UI]::GetDlgItem($h, 902); [UI]::SendMessage($c, $CB_SETCURSEL, [IntPtr]$Filter, [IntPtr]::Zero) | Out-Null; [UI]::SendMessage($h, $WM_COMMAND, [IntPtr]((1 -shl 16) -bor 902), $c) | Out-Null }
if ($Scale -ge 0) { $c = [UI]::GetDlgItem($h, 904); [UI]::SendMessage($c, $CB_SETCURSEL, [IntPtr]$Scale, [IntPtr]::Zero) | Out-Null; [UI]::SendMessage($h, $WM_COMMAND, [IntPtr]((1 -shl 16) -bor 904), $c) | Out-Null }
Start-Sleep -Milliseconds 600
if ($Tab -ge 0) { [UI]::SendMessage($h, $WM_COMMAND, [IntPtr](170 + $Tab), [IntPtr]::Zero) | Out-Null; Start-Sleep -Milliseconds 700 }
if ($Cmd -ge 0) { [UI]::SendMessage($h, $WM_COMMAND, [IntPtr]$Cmd, [IntPtr]::Zero) | Out-Null; Start-Sleep -Milliseconds 700 }
if ($SelectRow -ge 0) {
  $lv = [UI]::GetDlgItem($h, 1)
  $WM_KEYDOWN = 0x0100; $WM_KEYUP = 0x0101; $VK_HOME = 0x24; $VK_DOWN = 0x28
  [UI]::SendMessage($lv, $WM_KEYDOWN, [IntPtr]$VK_HOME, [IntPtr]::Zero) | Out-Null; [UI]::SendMessage($lv, $WM_KEYUP, [IntPtr]$VK_HOME, [IntPtr]::Zero) | Out-Null
  for ($k = 0; $k -lt $SelectRow; $k++) { [UI]::SendMessage($lv, $WM_KEYDOWN, [IntPtr]$VK_DOWN, [IntPtr]::Zero) | Out-Null; [UI]::SendMessage($lv, $WM_KEYUP, [IntPtr]$VK_DOWN, [IntPtr]::Zero) | Out-Null }
  Start-Sleep -Milliseconds 400
}
if ($HoverX -eq -2) {
  $gt = [UI]::GetDlgItem($h, 2); $WM_MOUSEMOVE = 0x0200; $hits = @()
  $save = New-Object UI+P; [UI]::GetCursorPos([ref]$save) | Out-Null
  $gr = New-Object UI+R; [UI]::GetWindowRect($gt, [ref]$gr) | Out-Null
  [UI]::SetCursorPos($gr.L + 300, $gr.T + 300) | Out-Null   # reálný kurzor dovnitř grafu, jinak TME_LEAVE tooltip hned schová
  Start-Sleep -Milliseconds 200
  foreach ($y in 30..140) { if ($y % 7 -ne 0) { continue }; foreach ($x in 20..900) { if ($x % 40 -ne 0) { continue }
    [UI]::SendMessage($gt, $WM_MOUSEMOVE, [IntPtr]::Zero, [IntPtr](($y -shl 16) -bor $x)) | Out-Null; Start-Sleep -Milliseconds 30
    $tt = [IntPtr]::Zero; do { $tt = [UI]::FindWindowEx([IntPtr]::Zero, $tt, "MppTip", [NullString]::Value); if ($tt -ne [IntPtr]::Zero -and [UI]::GetWindow($tt, 4) -eq $h -and [UI]::IsWindowVisible($tt)) { $hits += "($x,$y)"; if ($hits.Count -eq 3) { $tr = New-Object UI+R; [UI]::GetWindowRect($tt, [ref]$tr) | Out-Null; $tb = New-Object System.Drawing.Bitmap ($tr.Rt - $tr.L), ($tr.B - $tr.T); $tg = [System.Drawing.Graphics]::FromImage($tb); $tdc = $tg.GetHdc(); [UI]::PrintWindow($tt, $tdc, 2) | Out-Null; $tg.ReleaseHdc($tdc); $tb.Save((Join-Path $outDir "tooltip.png"), [System.Drawing.Imaging.ImageFormat]::Png) } } } while ($tt -ne [IntPtr]::Zero)
  } }
  [UI]::SetCursorPos($save.X, $save.Y) | Out-Null
  "TOOLTIP HITS: $($hits.Count) first=$($hits | Select-Object -First 6)"
}
if ($HoverX -ge 0) {
  $gt = [UI]::GetDlgItem($h, 2); $WM_MOUSEMOVE = 0x0200
  [UI]::SendMessage($gt, $WM_MOUSEMOVE, [IntPtr]::Zero, [IntPtr](($HoverY -shl 16) -bor $HoverX)) | Out-Null
  Start-Sleep -Milliseconds 300
  [UI]::SendMessage($gt, $WM_MOUSEMOVE, [IntPtr]::Zero, [IntPtr](($HoverY -shl 16) -bor ($HoverX + 2))) | Out-Null
  Start-Sleep -Milliseconds 500
  $tt = [IntPtr]::Zero; $found = "none"
  do { $tt = [UI]::FindWindowEx([IntPtr]::Zero, $tt, "tooltips_class32", [NullString]::Value); if ($tt -ne [IntPtr]::Zero -and [UI]::GetWindow($tt, 4) -eq $h) { $vis = [UI]::IsWindowVisible($tt); $sbt = New-Object System.Text.StringBuilder 2048; [UI]::SendMessage($tt, 0x000D, [IntPtr]2048, $sbt) | Out-Null; $found = "visible=$vis text=[$($sbt.ToString())]" } } while ($tt -ne [IntPtr]::Zero)
  "TOOLTIP: $found"
}
$sb = New-Object System.Text.StringBuilder 512; [UI]::SendMessage([UI]::GetDlgItem($h, 905), 0x000D, [IntPtr]512, $sb) | Out-Null; "SUMMARY: $($sb.ToString())"
$sb2 = New-Object System.Text.StringBuilder 4096; [UI]::SendMessage([UI]::GetDlgItem($h, 906), 0x000D, [IntPtr]4096, $sb2) | Out-Null; "DETAIL:`n$($sb2.ToString())"
$r = New-Object UI+R; [UI]::GetWindowRect($h, [ref]$r) | Out-Null
$bmp = New-Object System.Drawing.Bitmap ($r.Rt - $r.L), ($r.B - $r.T); $g = [System.Drawing.Graphics]::FromImage($bmp); $dc = $g.GetHdc(); [UI]::PrintWindow($h, $dc, 2) | Out-Null; $g.ReleaseHdc($dc)
$bmp.Save((Join-Path $outDir $Shot), [System.Drawing.Imaging.ImageFormat]::Png); "captured $Shot"
