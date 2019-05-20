const {app, BrowserWindow, nativeImage} = require('electron');
var http = require('http').createServer();
var io = require('socket.io')(http);
const execSync = require('child_process').execSync;

var devWin = null;
let win;
let screen;
let set = false;
let down = false;

let origWidth, origHeight, scaledWidth, scaledHeight, ratio;
let oldX, oldY;
app.on('ready', function(){
    win = new BrowserWindow({
        width:800,
        height:600,
        webPreferences:{
            nodeIntegration: true
        }
    });
    win.setMenuBarVisibility(true);
    win.loadFile('main.html');
});

// Quit when all windows are closed.
app.on('window-all-closed', () => {
  // On macOS it is common for applications and their menu bar
  // to stay active until the user quits explicitly with Cmd + Q
  if (process.platform !== 'darwin') {
    app.quit()
  }
});

//Runs when client connects
io.on('connection', function(socket) {
    console.log('A user connected');
    
    socket.on('disconnect', function() {
        console.log('user disconnected');
    });
    
    socket.on('message', function(from, msg){
        socket.broadcast.emit('message', from, msg);
    });
    
    socket.on("started", function(){
        console.log('Service Started.');
        socket.broadcast.emit("started");
    });

    socket.on('start', function(){
        if(devWin == null){
            openDeviceWindow();
        }
        socket.broadcast.emit('start');
    });

    socket.on('stop', function(){
        socket.broadcast.emit('stop');
    });

    socket.on('frame', function(frame){
        var temp = nativeImage.createFromDataURL('data:image/jpeg;base64,'+frame);
        var tempSize = temp.getSize();
        if(!set){
            origWidth = tempSize.width;
            origHeight = tempSize.height;
            ratio = temp.getAspectRatio();
            scaledWidth = Math.floor((screen.getPrimaryDisplay().size.height-100)*ratio);
            scaledHeight = screen.getPrimaryDisplay().size.height-50;
        }
        devWin.setSize(scaledWidth, scaledHeight);
        devWin.webContents.send('frame', frame);
    });

    socket.on('mouse-click', function(x, y, width, height){
        console.log('X: '+x+' Y: '+y);
        let mX = (x/width)*origWidth;
        let mY = (y/height)*origHeight;
        console.log('X: '+mX+' Y: '+mY);
        execSync('adb exec-out input tap '+mX+' '+mY);
    });

    socket.on('mouse-down', function(x, y, width, height){
        let mX = (x/width)*origWidth;
        let mY = (y/height)*origHeight;
        console.log('X: '+mX+' Y: '+mY);
        if(!down){
            execSync('adb exec-out input keyevent 0');
            down = true;
            oldX = x;
            oldY = y;
        }else{
            execSync('adb exec-out input swipe '+oldX+' '+oldY+' '+mX+' '+mY);
        }
    });

    socket.on('mouse-up', function(x, y, width, height){
        down = false;
        let mX = (x/width)*origWidth;
        let mY = (y/height)*origHeight;
        console.log('X: '+mX+' Y: '+mY);
        execSync('adb exec-out input keyevent 1');
    });
});

console.log('Waiting for connections...');
io.listen(5555);

function openDeviceWindow(){
    devWin = new BrowserWindow({
        width:800,
        height:600,
        webPreferences:{
            nodeIntegration: true
        }
    });
    screen = require('electron').screen;
    devWin.setMaximumSize(screen.getPrimaryDisplay().size.width, screen.getPrimaryDisplay().size.height-100);
    devWin.setMenuBarVisibility(false);
    devWin.loadFile('index.html');
}