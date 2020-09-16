package com.example.asbkeyboard;

import android.app.Activity;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.media.AudioManager;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

// DESCRIPTION :
// Braille dot code :
//  1   4
//  2   5
//  3   6
// The basic braille alphabet, braille numbers, braille punctuation and special symbols characters are constructed from six dots.
// These braille dots are positioned like the figure six on a die, in a grid of two parallel vertical lines of three dots each.
// From the six dots that make up the basic grid, 2^6 = 64 different configurations can be created.
// These 64 braille characters can be represented as 6 digit numbers like 1=a, 1345=n, 13456=y, 1356=z

public class ASBKeyboard extends InputMethodService implements KeyboardView.OnKeyboardActionListener {
    private KeyboardView kv;
    private Keyboard keyboard;

    static int commit_code_ds = 10;                             // Default delay for current braille code commitment delay in deciseconds.
    final long[] vibration_pattern_100ms = {0,100};             // {a,b} : Sleep for a milliseconds, vibrate for b milliseconds
    final long[] vibration_pattern_10ms = {0,10};               // {a,b} : Sleep for a milliseconds, vibrate for b milliseconds

    private boolean single_caps = false;                        // Caps for single letter
    private boolean word_caps = false;                          // Caps lock for entire word
    private boolean number_indicator = false;
    private boolean options = false;                            // Boolean value for options section. If options indicator code(123456) is typed, options value sets to true.
    private boolean vibration = true;                           // Boolean value for vibration option
    private boolean speech = true;                              // Boolean value for speech option

    static int touch_count = 0;                                 // Total touch counts for current braille code
    static int timer_count =0;                                  // Timer countdown for current braille code commitment

    static int[] current_code = {0,0,0,0,0,0};                  // Represents current braille code before commitment. Each digit represent a position in single braille code corresponding to its index value.
    // a="100000", b="120000", c="100400", d="100450"
    // After commitment, there 0's will be removed from string and all its left will be decimal code representation of a single braille code (for example d="145"), which are listed in arrays above;

    static int[] pressed_buttons_stack_array = new int[12]; // Array that where touched position coordinates are stored until all pointers (fingers) are lifted up.
    // pressed_buttons_stack_array[touch id]

    private TextToSpeech t1;


    // List that contains; braille code(column 0) representations of ascii codes for lowercase characters(column 1) .
    // Letters can be selected by default value or after using letter indicator(braille code=56)
    // row -> {braille dot code, lowercase char ascii code equivalent}
    static String[][] braille_alphabet = {
            {"1",       "97"},  // a
            {"12",      "98"},  // b
            {"14",      "99"},  // c
            {"16",      "231"}, // ç
            {"145",     "100"}, // d
            {"15",      "101"}, // e
            {"124",     "102"}, // f
            {"1245",    "103"}, // g
            {"126",     "287"}, // ğ
            {"125",     "104"}, // h
            {"35",      "141"}, // ı
            {"24",      "105"}, // i
            {"245",     "106"}, // j
            {"13",      "107"}, // k
            {"123",     "108"}, // l
            {"134",     "109"}, // m
            {"1345",    "110"}, // n
            {"135",     "111"}, // o
            {"246",     "246"}, // ö
            {"1234",    "112"}, // p
            {"12345",   "113"}, // q
            {"1235",    "114"}, // r
            {"234",     "115"}, // s
            {"146",     "351"}, // ş
            {"2345",    "116"}, // t
            {"136",     "117"}, // u
            {"1236",    "118"}, // v
            {"2456",    "119"}, // w
            {"1346",    "120"}, // x
            {"13456",   "121"}, // y
            {"1356",    "122"}, // z

            // Some punctuation signs can be selected by default single braille codes (like ?=236)
            // This list contains punctuation signs that can be selected via default single braille code
            {"2",       "44"},  // ,
            {"23",      "59"},  // ;
            {"25",      "58"},  // :
            {"256",     "46"},  // .
            {"236",     "63"},  // ?
            {"235",     "33"},  // !
            {"3",       "39"},  // '
            {"36",      "45"},  // -
    };
    // List that contains; braille code(column 0) representations of braille code for numbers(column 1), after number indicator (braille code=3456).
    // Numbers can be selected by using number indicator(braille code=3456) before desired number.
    static String[][] braille_numbers = {
            {"1",       "49"}, // 1
            {"12",      "50"}, // 2
            {"14",      "51"}, // 3
            {"145",     "52"}, // 4
            {"15",      "53"}, // 5
            {"124",     "54"}, // 6
            {"1245",    "55"}, // 7
            {"125",     "56"}, // 8
            {"24",      "57"}, // 9
            {"245",     "48"}, // 0
    };

    // Some punctuation signs can be selected by default single braille codes (like ?=236)
    // Some other punctuation signs can be selected by using different indicators (like 3,5,456).
    static String[][] braille_punctuations_3 = {
            {"2356",    "34"},  // "
    };
    static String[][] braille_punctuations_5 = {
            {"126",     "40"},  // (
            {"345",     "41"},  // )
    };
    static String[][] braille_punctuations_456 = {
            {"34",      "47"},  // /
            {"16",      "92"},  // \
    };


    private String voice_on;
    private String voice_off = "";
    private String voice_single_caps;
    private String voice_word_caps ;
    private String voice_number_indicator;
    private String voice_options_menu;
    private String voice_voiceover;
    private String voice_typing_delay;
    private String voice_vibration;
    private String voice_delete;
    private String voice_space;
    private String voice_enter;



    public int getLength(int number){                                                               // Returns Number of digits of a number
        int length = 0;
        long temp = 1;
        while (temp <= number) {
            length++;
            temp *= 10;
        }
        return length;
    }

    @Override
    public View onCreateInputView() {
        kv = (KeyboardView)getLayoutInflater().inflate(R.layout.keyboard,null);
        keyboard = new Keyboard(this,R.xml.qwerty);
        kv.setKeyboard(keyboard);
        kv.setOnKeyboardActionListener(this);

        t1=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    if (Locale.getDefault().getCountry().equals("US") ){
                        t1.setLanguage(Locale.US);
                        voice_on = "On";
                        voice_off = "Off";
                        voice_single_caps = "Single caps ";
                        voice_word_caps = "Word caps ";
                        voice_number_indicator = "Number indicator";
                        voice_options_menu = "Options menu ";
                        voice_voiceover = "Voice over ";
                        voice_typing_delay = "Typing delay ";
                        voice_vibration = "Vibration";
                        voice_delete = "Delete";
                        voice_space = "Space";
                        voice_enter = "Enter";

                    }else if(Locale.getDefault().getCountry().equals("UK")){
                        t1.setLanguage(Locale.UK);
                        voice_on = "On";
                        voice_off = "Off";
                        voice_single_caps = "Single caps ";
                        voice_word_caps = "Word caps ";
                        voice_number_indicator = "Number indicator";
                        voice_options_menu = "Options menu ";
                        voice_voiceover = "Voice over ";
                        voice_typing_delay = "Typing delay ";
                        voice_vibration = "Vibration";
                        voice_delete = "Delete";
                        voice_space = "Space";
                        voice_enter = "Enter";
                    }
                    else{
                        Locale locale = new Locale("tr", "TR");
                        t1.setLanguage(locale);
                        voice_on = "Açık";
                        voice_off = "Kapalı";
                        voice_single_caps = "Büyük harf";
                        voice_word_caps = "Büyük harf kelime";
                        voice_number_indicator = "Numara belirteçi";
                        voice_options_menu = "Ayarlar menüsü ";
                        voice_voiceover = "Seslendirme ";
                        voice_typing_delay = "Yazma gecikmesi ";
                        voice_vibration = "Titreşim ";
                        voice_delete = "Silindi";
                        voice_space = "Boşluk";
                        voice_enter = "Giriş";
                    }
                }
            }
        });

        return kv;
    }

    private void playClick(int i) {                                                                 // Sound effects for buttons
        AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
        switch(i)
        {
            case 2:
                am.playSoundEffect(AudioManager.FX_KEYPRESS_SPACEBAR);
                break;
            case Keyboard.KEYCODE_DONE:
            case 1:
                am.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN);
                break;
            case 3:
                am.playSoundEffect(AudioManager.FX_KEYPRESS_DELETE);
                break;
            default: am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD);
        }

    }

    @Override
    public void onPress(int i) {
        //Log.i("public void","onPress");
    }

    @Override
    public void onRelease(int i) {
        //Log.i("public void","onRelease");
    }

    @Override
    public void onKey(int key_code, int[] ints) {
        // key_code = android.codes (in xml)
        Log.i("options",""+options);
        Log.i("vibration",""+vibration);
        final InputConnection ic = getCurrentInputConnection();
        //playClick(key_code);
        final Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (touch_count < 6){                                                               // max touch count is 6
            //Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibration==true) {
                vibrator.vibrate(vibration_pattern_10ms, -1); // Repeat: 0=forever, -1=not repeat
            }
            pressed_buttons_stack_array[touch_count]= key_code;
        }
        if (touch_count==0) { // If its first touch, start timer for braille code commitment. Selected braille code will be committed after waiting idle(not touching any other button) for specified time.
            final Timer myTimer=new Timer();
            TimerTask task =new TimerTask() {
                @Override
                public void run() {
                    // empty stack
                    timer_count++;   // 1 timer_count = 1 period in schedule
                    Log.i("Timer",""+ timer_count);
                    if(timer_count == commit_code_ds ) {                                     // Countdown for commitment.

                        // Commitment Process Start_______________________________________
                        for (int index=0 ; index < pressed_buttons_stack_array.length-1 ; index++){
                            if (pressed_buttons_stack_array[index] == 0){
                                break;
                            }
                            int length_of_array_item = getLength(pressed_buttons_stack_array[index] );
                            if ( length_of_array_item == 1 ){                               // If pressed button of current index is belongs to main area (1|2|3|4|5|6)
                                current_code[pressed_buttons_stack_array[index] - 1 ] = pressed_buttons_stack_array[index];
                            }
                            else{                                                           // If pressed button of current index is a cross area (12|14|145 ...)
                                int number = pressed_buttons_stack_array[index];
                                if ( (current_code[(number%10)-1] == 0) ) {
                                    current_code[(number % 10) - 1] = 8;                    // 8 means this digit of current code could be selected or not, and it has a lower possibility if its selected.
                                }                                                           // which means this button has a lower priority while deciding currently typed braille code
                                number = number / 10;
                                if ( (current_code[number-1] == 0) ) {
                                    current_code[(number % 10) - 1] = 7;                    // 7 means this digit of current code could be selected or not, and it has a higher possibility if its selected.
                                }                                                           // which means this button has a higher priority while deciding currently typed braille code
                            }
                        }

                        // Process 7 s
                        for (int i=0; i<6 ; i++){
                            if (touch_count==0) {
                                current_code[i] = 0;
                            }else{
                                if (current_code[i] > 0 & current_code[i] < 8) {
                                    if (current_code[i] == 7) {
                                        current_code[i] = i+1;
                                    }
                                    touch_count--;
                                }
                            }
                        }
                        // Process 8 s
                        for (int i=0; i<6 ; i++){
                            if (touch_count==0 & current_code[i]==8 ) {
                                current_code[i] = 0;
                            }else{
                                if (current_code[i] > 0) {
                                    if (current_code[i] == 8) {
                                        current_code[i] = i+1;
                                        touch_count--;
                                    }
                                }
                            }
                        }

                        // Translate current braille code to string. For comparison in list
                        String current_code_str = "";
                        for (int i=0;i<6;i++){
                            if(current_code[i]!=0){
                                current_code_str = current_code_str+current_code[i];
                            }
                        }

                        // Find if current code string presents in any array. If it exists, then get the ascii code of that character and commit that character
                        if (current_code_str.equals("6") & options==false){                 // If current braille code is capitalize letter
                            Log.i("caps","if");
                            if (single_caps==true){                                         // If its second capitalize letter, it should capitalize entire word until next space.
                                word_caps=true;
                                t1.speak(""+voice_word_caps, TextToSpeech.QUEUE_FLUSH, null);
                                Log.i("word_caps", "true");
                            }else {
                                single_caps = true;
                                t1.speak(""+voice_single_caps, TextToSpeech.QUEUE_FLUSH, null);
                                Log.i("single_caps", "true");
                            }
                        }else if(current_code_str.equals("3456") & options==false){         // If current braille code is number indicator
                            number_indicator = true;
                            t1.speak(""+voice_number_indicator, TextToSpeech.QUEUE_FLUSH, null);
                            Log.i("number_indicator",""+number_indicator);
                        }else if(current_code_str.equals("123456")){                        // If current braille code is options menu indicator
                            if (options==false) {
                                options = true;
                                t1.speak(""+voice_options_menu+voice_on, TextToSpeech.QUEUE_FLUSH, null);
                            }else if (options==true){
                                options = false;
                                t1.speak(""+voice_options_menu+voice_off, TextToSpeech.QUEUE_FLUSH, null);
                            }
                            Log.i("options",""+options);
                        }else{                                                              // If currently typed braille code is not an indicator
                            if (number_indicator==true){                                    // If number indicator is open
                                for (int i = 0; i < 10; i++) {
                                    if (current_code_str.equals(braille_numbers[i][0])) {
                                        // Commit current braille code
                                        Log.i("number indicator", "if");
                                        char code = (char) Integer.parseInt(braille_numbers[i][1]);
                                        if(speech==true) {
                                            t1.speak("" + code, TextToSpeech.QUEUE_FLUSH, null);
                                        }
                                        ic.commitText(String.valueOf(code), 1);  // Commit
                                        break;
                                    }
                                }
                            }else if(options==true){                                        // If options indicator is open
                                switch (current_code_str){
                                    case "4":
                                        speech = true;
                                        t1.speak(""+voice_voiceover+voice_on, TextToSpeech.QUEUE_FLUSH, null);
                                        break;
                                    case "1":
                                        speech = false;
                                        t1.speak(""+voice_voiceover+voice_off, TextToSpeech.QUEUE_FLUSH, null);
                                        break;
                                    case "5":                                               // Increase commitment delay
                                        if(commit_code_ds < 15){
                                            commit_code_ds++;
                                        }
                                        t1.speak(""+voice_typing_delay+commit_code_ds, TextToSpeech.QUEUE_FLUSH, null);
                                        break;
                                    case "2":                                               // Decrease commitment delay
                                        if(commit_code_ds > 2){
                                            commit_code_ds--;
                                        }
                                        t1.speak(""+voice_typing_delay+commit_code_ds, TextToSpeech.QUEUE_FLUSH, null);
                                        break;
                                    case "6":
                                        vibration = true;
                                        t1.speak(""+voice_vibration+voice_on, TextToSpeech.QUEUE_FLUSH, null);
                                        break;
                                    case "3":
                                        vibration = false;
                                        t1.speak(""+voice_vibration+voice_off, TextToSpeech.QUEUE_FLUSH, null);
                                        break;
                                }
                                Log.i("Speech",""+vibration);
                                Log.i("Commitment speed",""+commit_code_ds);
                                Log.i("vibration",""+vibration);
                            }
                            else {                                                          // If options current code is not an indicator
                                for (int i = 0; i < 39; i++) {
                                    if (current_code_str.equals(braille_alphabet[i][0])) {
                                        // Commit current braille code
                                        char code = (char) Integer.parseInt(braille_alphabet[i][1]);
                                        if (Character.isLetter(code) && (single_caps | word_caps)) {  // If caps lock is open, convert to upper case
                                            code = Character.toUpperCase(code);
                                            single_caps = false;
                                        }
                                        if(speech==true) {
                                            t1.speak("" + code, TextToSpeech.QUEUE_FLUSH, null);
                                        }
                                        single_caps = false;
                                        ic.commitText(String.valueOf(code), 1);  // Commit
                                        break;
                                    }
                                }
                            }
                        }
                        // Commitment Process End_______________________________________

                        //Log.i("str",""+current_code_str);
                        //Log.i("pressed_buttons_stack_array", "[0]="+pressed_buttons_stack_array[0]+",[1]="+pressed_buttons_stack_array[1]+",[2]="+pressed_buttons_stack_array[2]+",[3]="+pressed_buttons_stack_array[3]+",[4]="+pressed_buttons_stack_array[4]+",[5]="+pressed_buttons_stack_array[5]);
                        //Log.i("current_code",""+current_code[0]+current_code[1]+current_code[2]+current_code[3]+current_code[4]+current_code[5]);
                        if (vibration == true) {
                            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                            vibrator.vibrate(vibration_pattern_100ms, -1); // Repeat: 0=forever, -1=not repeat
                        }
                        touch_count = 0;
                        timer_count = 0;
                        for (int i=0 ; i<6 ; i++){
                            current_code[i] = 0;
                        }
                        for (int i=0 ; i<12 ; i++){
                            pressed_buttons_stack_array[i] = 0;
                        }
                        myTimer.cancel();
                    }
                }
            };
            myTimer.schedule(task, 0, 100); // Call every 100 ms
        }
        else {                                                                              // When another button is clicked
            timer_count = 0;                                                                // When another button is clicked, timer resets.
            //Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibration == true) {
                vibrator.vibrate(vibration_pattern_10ms, -1); // Repeat: 0=forever, -1=not repeat
            }
        }
        touch_count++;
        //Log.i("Touch count:", "" + touch_count);
    }

    @Override
    public void onText(CharSequence charSequence) {

    }

    @Override
    public void swipeLeft() {   // Delete
        Log.i("Swipe","left");
        final InputConnection ic = getCurrentInputConnection();
        //playClick(key_code);
        ic.deleteSurroundingText(1,0);
        playClick(12); // Sound
        if (speech==true) {
            t1.speak(""+voice_delete, TextToSpeech.QUEUE_FLUSH, null);
        }
        if (vibration==true) {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(vibration_pattern_100ms, -1); // Repeat: 0=forever, -1=not repeat
        }
    }

    @Override
    public void swipeRight() {  // Space
        Log.i("Swipe","right");
        final InputConnection ic = getCurrentInputConnection();
        //playClick(key_code);
        char code = (char)32;
        word_caps = false;
        number_indicator = false;
        ic.commitText(String.valueOf(code), 1);  // Commit
        if (speech==true) {
            t1.speak(""+voice_space, TextToSpeech.QUEUE_FLUSH, null);
        }
        if (vibration==true) {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(vibration_pattern_100ms, -1); // Repeat: 0=forever, -1=not repeat
        }
    }

    @Override
    public void swipeDown() {   // Close keyboard
        Log.i("Swipe","down");
        /*final InputConnection ic = getCurrentInputConnection();
        //playClick(key_code);
        char code = (char)27; //127=del, 8=backspace
        ic.commitText(String.valueOf(code), 1);  // Commit

         */

        if (vibration==true) {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(vibration_pattern_100ms, -1); // Repeat: 0=forever, -1=not repeat
        }
    }

    @Override
    public void swipeUp() { // New line
        Log.i("Swipe","up");
        final InputConnection ic = getCurrentInputConnection();
        word_caps=false;
        single_caps=true;
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));
        if (speech = true) {
            t1.speak(""+voice_enter, TextToSpeech.QUEUE_FLUSH, null);
        }
        if (vibration==true) {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(vibration_pattern_100ms, -1); // Repeat: 0=forever, -1=not repeat
        }
    }
}