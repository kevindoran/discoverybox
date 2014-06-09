import subprocess
import os
import configparser
from os.path import expanduser
import glob
import urllib.request
import shutil
import threading
import time

def playVideo(video):
    player = shutil.which('omxplayer')
    if player is None:
        player = shutil.which('mplayer')
    
    if player is None:
        shutdown()
        # Return?
    subprocess.call([player, video])
    #os.remove(video)    
    
def shutdown():
    print("Shutting down.")

# Turn on wifi and check for connection.

# Check for updated code.
result = subprocess.call(['hg', 'incoming', '--quiet'])
if result == 0:
    # There are changes to download.
    result = subprocess.call(['hg', 'pull', '--quiet'])
    if result == 0:
        # Changes retrieved successfully. 
        result = subprocess.call(['hg', 'update', '--quiet'])
        if result == 0:
            # Updated successfully. The Java application can be re-packaged.
            os.chdir("../vimeodownloader")
            subprocess.call(['mvn', 'package'])
            
# Read initialization file.         
config= configparser.ConfigParser()
config.read('./discoverybox.ini')

videoDirectory = expanduser(config['DEFAULT']['videoDirectory'])
maxNoOfMovies = int(config['DEFAULT']['maxNoOfMovies'])
downloadThreshold = int(config['DEFAULT']['downloadThreshold'])

# Find current movies
videos = glob.glob(videoDirectory + '/*.mp4')
blankStart = False
if len(videos) == 0:
    blankStart = True
    startPos = 0
    count = 1
    url = subprocess.check_output(['java', '-jar', '../vimeodownloader/target/vimeodownloader-1.0-SNAPSHOT-jar-with-dependencies.jar', str(count), str(startPos)], universal_newlines=True)
    print(url)
    # Either cookies or redirect is not handled well with urlretrieve
    # urllib.request.urlretrieve(url, videoDirectory + "1.mp4")
    subprocess.call(['wget', '-O' + videoDirectory + "/1.mp4", url])
    
videos = glob.glob(videoDirectory + '/*.mp4')
videos.sort()

if len(videos) == 0:
    # Video download didn't work, so shutdown.
    shutdown()
    
# Play the oldest movie in another thread.
movieThread = threading.Thread(target=playVideo, args = (videos.pop(0),))
movieThread.start()


if len(videos) < downloadThreshold:
    if blankStart:
        # StartPos is increased as the latest movie has already been downloaded.
        startPos = 1
    else:
        startPos = 0
    count = maxNoOfMovies - len(videos)
    urls = subprocess.check_output(['java', '-jar', '../vimeodownloader/target/vimeodownloader-1.0-SNAPSHOT-jar-with-dependencies.jar', str(count), str(startPos)],universal_newlines=True)
    toDownload = urls.split()
    for x in xrange(len(toDownload)):
        print(urls[x])
        #urllib.request.urlretrieve(urls[x], videoDirectory + str(x) + ".mp4")
        subprocess.call(['wget', '-O' + videoDirectory + "/" + str(x+startPos) + ".mp4", urls[x]])
		
# Wait for movie to finish.
while movieThread.isAlive():
    time.sleep(3)
    
shutdown()           
            
            
            
            
