# Start MTV Application Servers

Write-Host "Starting MTV Application..." -ForegroundColor Green

# Start backend server
Write-Host "Starting Backend Server..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-Command", "cd '$PSScriptRoot\server'; node app.js" -NoNewWindow

# Wait a moment for backend to start
Start-Sleep -Seconds 3

# Start frontend server
Write-Host "Starting Frontend Server..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-Command", "cd '$PSScriptRoot\client'; npm start" -NoNewWindow

Write-Host "Both servers are starting..." -ForegroundColor Green
Write-Host "Backend: http://localhost:5000" -ForegroundColor Cyan
Write-Host "Frontend: http://localhost:3000" -ForegroundColor Cyan
Write-Host ""
Write-Host "Press any key to stop servers..." -ForegroundColor Red
[Console]::ReadKey() | Out-Null

# Kill all node processes (this is a simple way to stop the servers)
Write-Host "Stopping servers..." -ForegroundColor Yellow
Get-Process node -ErrorAction SilentlyContinue | Stop-Process -Force
