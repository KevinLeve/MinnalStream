# MinnalStream hotspot receiver launcher. Requires ffplay.exe (FFmpeg).
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[Windows.Forms.Application]::EnableVisualStyles()
$script:ffplay = (Get-Command ffplay.exe -ErrorAction SilentlyContinue).Source
if (-not $script:ffplay) {
  foreach ($p in @("$env:ProgramFiles\ffmpeg\bin\ffplay.exe", "$env:ProgramFiles\FFmpeg\bin\ffplay.exe", "$env:LOCALAPPDATA\Microsoft\WinGet\Links\ffplay.exe")) {
    if (Test-Path $p) { $script:ffplay = $p; break }
  }
}
$form = New-Object Windows.Forms.Form
$form.Text = 'MinnalStream Receiver'
$form.Size = New-Object Drawing.Size(620,510)
$form.StartPosition = 'CenterScreen'
$form.BackColor = [Drawing.Color]::FromArgb(15,20,31)
$form.ForeColor = [Drawing.Color]::White
$form.Font = New-Object Drawing.Font('Segoe UI',10)
$title = New-Object Windows.Forms.Label
$title.Text = 'MinnalStream Receiver'
$title.Font = New-Object Drawing.Font('Segoe UI Semibold',22,[Drawing.FontStyle]::Bold)
$title.Location = New-Object Drawing.Point(24,20); $title.Size = New-Object Drawing.Size(550,42)
$form.Controls.Add($title)
$ip = '192.168.137.1'
try {
  $candidate = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object { $_.IPAddress -like '192.168.137.*' -and $_.IPAddress -ne '192.168.137.255' } | Select-Object -First 1
  if ($candidate) { $ip = $candidate.IPAddress }
} catch {}
$desc = New-Object Windows.Forms.Label
$desc.Text = "Phone should connect to your laptop hotspot. Default hotspot receiver IP:`r`n$ip`r`nPort: 5000 (TCP)"
$desc.Location = New-Object Drawing.Point(28,82); $desc.Size = New-Object Drawing.Size(550,75)
$form.Controls.Add($desc)
$instructions = New-Object Windows.Forms.TextBox
$instructions.Multiline = $true; $instructions.ReadOnly = $true; $instructions.ScrollBars = 'Vertical'
$instructions.Location = New-Object Drawing.Point(28,170); $instructions.Size = New-Object Drawing.Size(550,170)
$instructions.BackColor = [Drawing.Color]::FromArgb(8,12,20); $instructions.ForeColor = [Drawing.Color]::FromArgb(215,225,240)
$instructions.Text = "1. Turn ON Windows Mobile hotspot.`r`n2. Connect the iQOO to that hotspot.`r`n3. Install FFmpeg (ffplay) if not already installed.`r`n4. Click Start receiver below; allow the firewall prompt if shown.`r`n5. In MinnalStream Android app enter $ip and port 5000, then start streaming.`r`n6. To record, open OBS and capture the MinnalStream video window.`r`n`r`nNote: the first frame may take a moment. This is a preview; audio is not transmitted yet."
$form.Controls.Add($instructions)
$status = New-Object Windows.Forms.Label
$status.Location = New-Object Drawing.Point(28,352); $status.Size = New-Object Drawing.Size(550,32)
if ($script:ffplay) { $status.Text = "FFmpeg found: $script:ffplay"; $status.ForeColor = [Drawing.Color]::LightGreen }
else { $status.Text = 'FFmpeg / ffplay not found. Install it first.'; $status.ForeColor = [Drawing.Color]::Salmon }
$form.Controls.Add($status)
$start = New-Object Windows.Forms.Button
$start.Text = 'Start receiver'; $start.Location = New-Object Drawing.Point(28,400); $start.Size = New-Object Drawing.Size(250,42)
$start.BackColor = [Drawing.Color]::FromArgb(20,112,180); $start.ForeColor = [Drawing.Color]::White
$start.Add_Click({
  if (-not $script:ffplay) {
    [Windows.Forms.MessageBox]::Show('FFmpeg (ffplay.exe) is required. Run Setup-Windows.bat or install FFmpeg from https://ffmpeg.org/download.html, then reopen MinnalStream Receiver.','FFmpeg missing','OK','Warning') | Out-Null
    return
  }
  try {
    $args = @('-window_title','MinnalStream Video','-f','h264','-fflags','nobuffer','-flags','low_delay','-framedrop','-probesize','32','-analyzeduration','0','tcp://0.0.0.0:5000?listen=1')
    Start-Process -FilePath $script:ffplay -ArgumentList $args
    $status.Text = 'Receiver listener launched on TCP port 5000. Connect from the phone app.'
    $status.ForeColor = [Drawing.Color]::LightGreen
  } catch { [Windows.Forms.MessageBox]::Show($_.Exception.Message,'Could not start receiver') | Out-Null }
})
$form.Controls.Add($start)
$obs = New-Object Windows.Forms.Button
$obs.Text = 'Open OBS for recording'; $obs.Location = New-Object Drawing.Point(294,400); $obs.Size = New-Object Drawing.Size(284,42)
$obs.Add_Click({
  $exe = (Get-Command obs64.exe -ErrorAction SilentlyContinue).Source
  if (-not $exe) { foreach ($p in @("$env:ProgramFiles\obs-studio\bin\64bit\obs64.exe", "${env:ProgramFiles(x86)}\obs-studio\bin\64bit\obs64.exe")) { if (Test-Path $p) { $exe = $p; break } } }
  if ($exe) { Start-Process $exe } else { Start-Process 'https://obsproject.com/download' }
})
$form.Controls.Add($obs)
[void]$form.ShowDialog()
