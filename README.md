# Multicast-Group-Messenger
Implemented ISIS algorithm with modification to achieve FIFO-Total ordering of messages that is multi-casted to 5 AVDs. Ordering of the message is preserved even during the failure of the application.

## Steps To Run The Program:
1. Use python-2.7 to run the commands
2. Create AVD:
```
python create_avd.py
python updateavd.py
```
3. Start the AVD:
```
python run_avd.py 5
```
4. Starting the emulator network:
```
python set_redir.py 10000
```
5. Test the program by running the grading script along with the build APK file of the program. (The grading is done in phase as mentioned below)
```
.\groupmessenger2-grading.exe app-debug_groupmessenger.apk
```

## Testing Phases:
1. 4%: Group messenger provides total-FIFO ordering guarantees with messages stored in the content provider.
2. 6%: Group messenger provides total-FIFO ordering guarantees with message stored in the content provider for all correct app          instances under a single app failure.
