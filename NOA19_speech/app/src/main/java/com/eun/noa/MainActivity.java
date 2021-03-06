package com.eun.noa;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.kakao.sdk.newtoneapi.SpeechRecognizeListener;
import com.kakao.sdk.newtoneapi.SpeechRecognizerClient;
import com.kakao.sdk.newtoneapi.SpeechRecognizerManager;
import com.kakao.sdk.newtoneapi.TextToSpeechClient;
import com.kakao.sdk.newtoneapi.TextToSpeechManager;
import com.kakao.sdk.newtoneapi.TextToSpeechListener;

import android.app.Activity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;


public class MainActivity extends Activity {
    private static final int REQUEST_CODE_AUDIO_AND_WRITE_EXTERNAL_STORAGE = 0;
    private static final int VIBRATESECONDS = 200;
    private static final int AMPLITUDE = 30;
    private static final String TAG = "MainActivity";   // 로그에 사용
    private static final String FILE_NAME = "destination.txt";
    private static final String url = "http://192.168.1.8" + ":8080/ros_js.html";

    // 음성 안내 순서를 알기 위한 string 변수
    // "_"는 "예/아니요"로 하는 음성 인식
    private static final String EXPLANATION = "EXPLANATION";
    private static final String SEARCH = "SEARCH";
    private static final String REASK = "REASK";
    private static final String REASK_ = "REASK_";
    private static final String NAVIGATE = "NAVIGATE";
    private static final String REASK_YES = "YES";
    private static final String REASK_NO = "NO";
    private static final String ARRIVAL = "ARRIVAL";
    private static final String PREV_DESTINATION_ = "PREV_DESTINATION_";
    private static final String RERECORD = "RERECORD";
    private static final String RERECORD_ = "RERECORD_";
    private static final String RESTART_ = "RESTART_";

    //    private static boolean TRAFFIC_BLUE = false;
    private static long backKeyPressedTime;                    // 앱종료 위한 백버튼 누른시간
    private static String speech_text;                         // 음성인식한 단어 저장
    private static String state_text = null;                   // 음성 안내 상태 저장 변수 -> 밑의 변수랑 같이 이용
    private static String destination;
    private static String prev_destination;
    private static String prev_state;
    private static String flag_traffic;

    // 레이아웃 변수
    private static ImageButton button;
    private static TextView textView;
    private static WebView mWebView;
    private static Button reloadbutton;
    private static Vibrator vibrator;
    private static AudioManager audioManager;

    private static TTS tts;
    private static STT stt;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 음성인식을 하는데 필요한 권한 묻기 => 마이크, 인터넷 권한 필요
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE_AUDIO_AND_WRITE_EXTERNAL_STORAGE);
        }

        // 레이아웃 변수 설정
        mWebView = (WebView) findViewById(R.id.webView);
        button = findViewById(R.id.bt);
        reloadbutton = findViewById(R.id.bt_reload);
        textView = findViewById(R.id.tv_speech);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // 웹뷰 설정
        mWebView.loadUrl(url);                                          // 서버에 있는 html 파일
        mWebView.addJavascriptInterface(new WebBridge(), "NOA");  // js에서 안드로이드 함수를 쓰기 위한 브릿지 설정 -> window.NOA.functionname();
        mWebView.setWebViewClient(new WebViewClient());                 // 웹뷰 클라이언트
        mWebView.setWebChromeClient(new WebChromeClient());             // 웹뷰 크롬 클라이언트
        mWebView.getSettings().setJavaScriptEnabled(true);              // 웹뷰에서 자바스크립트 사용 가능하게
        mWebView.setWebContentsDebuggingEnabled(true);                  // 크롬에서 웹뷰 디버깅 가능하게


        // 음성합성 초기화
        TextToSpeechManager.getInstance().initializeLibrary(getApplicationContext());
        tts = new TTS();
        // 음성인식 초기화
        SpeechRecognizerManager.getInstance().initializeLibrary(this);
        stt = new STT();

        // 앱 켰을 때 시작
        if (state_text == null) {
            speech_text = getString(R.string.str_start);
            flag_traffic = new String("-1");
            state_text = new String(EXPLANATION);
            tts.ttsClient.play(speech_text);
        }

        // rosbridge랑 연결이 끊겼을 경우 html 다시 로드하는 버튼
        reloadbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mWebView.reload();
            }
        });


        // 이전 목적지
        button.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                vibrator.vibrate(VibrationEffect.createOneShot(VIBRATESECONDS, AMPLITUDE));

                FileInputStream fis = null;
                try {
                    fis = openFileInput(FILE_NAME);
                    InputStreamReader isr = new InputStreamReader(fis);
                    BufferedReader br = new BufferedReader(isr);
                    StringBuilder sb = new StringBuilder();
                    String text;

                    while ((text = br.readLine()) != null) {
                        sb.append(text).append("\n");
                    }

                    textView.setText(sb.toString());
                    prev_destination = sb.toString();

                    speech_text = "이전 목적지는" + prev_destination + getString(R.string.str_prev_destination);
                    tts.ttsClient.play(speech_text);
                    state_text = PREV_DESTINATION_;

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (fis != null) {
                        try {
                            fis.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_PLAY_SOUND);
                return true;
            }
        });

        // 음성인식하는 버튼
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                button.setEnabled(false);

                switch (state_text) {
                    case EXPLANATION:                   // 목적지 묻기
                        state_text = SEARCH;// 검색을 하기 위해 state_text 설정
                        speech_text = getString(R.string.str_definition);// 음성 합성할 문장으로 speech_text 설정
                        tts.ttsClient.play(speech_text);// 음성 합성
                        break;
                    case SEARCH:                        // 검색하기
                        // 음성 인식 시작할 때 나타나는 토스트 메세지
                        Toast.makeText(MainActivity.this, "음성인식을 시작합니다.", Toast.LENGTH_SHORT).show();
                        stt.client.startRecording(true);
                        break;
                    case REASK:                         // 인식한 목적지가 맞는지 확인하기
                        state_text = REASK_;
                        destination = textView.getText().toString();
                        speech_text = "목적지가 " + destination + "입니까?";
                        tts.ttsClient.play(speech_text);
                        break;
                    case REASK_:                  // 목적지 재확인 시 대답
                        Toast.makeText(MainActivity.this, "음성인식을 시작합니다.", Toast.LENGTH_SHORT).show();
                        stt.client.startRecording(true);
                        break;
                    case REASK_NO:                      // 목적지가 잘못 인식되었을 경우
                        state_text = SEARCH;
                        speech_text = getString(R.string.str_no);
                        tts.ttsClient.play(speech_text);
                        break;
                    case REASK_YES:                     // 목적지가 잘 인식되었을 경우
                        state_text = NAVIGATE;
                        speech_text = getString(R.string.str_navigate);
                        tts.ttsClient.play(speech_text);

                        mWebView.loadUrl("javascript:setflag('" + destination + "')");// js의 함수 setflag() : 목적지 이름을 변수에 저장

                        FileOutputStream fos = null;
                        try {
                            fos = openFileOutput(FILE_NAME, MODE_PRIVATE);
                            fos.write(destination.getBytes());
                            textView.setText(destination);
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            if (fos != null) {
                                try {
                                    fos.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        break;
                    case NAVIGATE:                      // 안내하기
                        state_text = RESTART_;
                        speech_text = getString(R.string.str_restart);
                        tts.ttsClient.play(speech_text);
                        break;
                    case RESTART_:                       // 목적지를 다시 설정할 경우
                        Toast.makeText(MainActivity.this, "음성인식을 시작합니다.", Toast.LENGTH_SHORT).show();
                        stt.client.startRecording(true);
                        break;
                    case ARRIVAL:                   // 도착했을 경우
                        state_text = EXPLANATION;
                        speech_text = getString(R.string.str_arrival);
                        tts.ttsClient.play(speech_text);
                        break;
                    case PREV_DESTINATION_:              // 이전 목적지로 갈 것인지에 대한 대답
                        Toast.makeText(MainActivity.this, "음성인식을 시작합니다.", Toast.LENGTH_SHORT).show();
                        stt.client.startRecording(true);
                        break;
                    case RERECORD:                      // 예/아니오 외에 다른 대답이 나올 경우
                        state_text = RERECORD_;
                        speech_text = getString(R.string.str_reanswer);
                        tts.ttsClient.play(speech_text);
                        break;
                    case RERECORD_:                     // REANSWER에 대한 대답
                        state_text = prev_state;
                        Toast.makeText(MainActivity.this, "음성인식을 시작합니다.", Toast.LENGTH_SHORT).show();
                        stt.client.startRecording(true);
                        break;
                    default:
                        button.setEnabled(true);
                        break;
                }
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_PLAY_SOUND);
            }

        });
    }

    /***************************************************************************************************************************/

    //뒤로가기 2번하면 앱종료
    @Override
    public void onBackPressed() {
        //1번째 백버튼 클릭
        if (System.currentTimeMillis() > backKeyPressedTime + 2000) {
            backKeyPressedTime = System.currentTimeMillis();
            Toast.makeText(this, getString(R.string.APP_CLOSE_BACK_BUTTON), Toast.LENGTH_SHORT).show();
        }
        //2번째 백버튼 클릭 (종료)
        else {
            AppFinish();
        }
    }

    //앱종료
    public void AppFinish() {
        finish();
        System.exit(0);
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    /***************************************************************************************************************************/
    /***************************************************************************************************************************/
    /***************************************************************************************************************************/

    // 자바스크립트에서 안드로이드 함수 호출할 때 사용
    // 자바스크립트 코드 : window.NOA.함수이름()
    class WebBridge {
        @JavascriptInterface
        public void SuccessArrival() {  // 목적지에 도착할 경우 실행
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView textView = findViewById(R.id.tv_speech);
                    textView.setText("Success Arrival");
                    button.setEnabled(false);
                    state_text = ARRIVAL;

                    // 다시 처음으로
                    speech_text = getString(R.string.str_arrival);
                    state_text = EXPLANATION;
                    tts.ttsClient.play(speech_text);
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_PLAY_SOUND);
                    Log.i(TAG, "도착");
                }
            });
        }

        @JavascriptInterface
        public void thereIsStairs() {       // 계단 인식할 경우 실행
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView textView = findViewById(R.id.tv_speech);
                    textView.setText("There are Stairs");

                    if (!tts.ttsClient.isPlaying()) {
                        button.setEnabled(false);
                        speech_text = getString(R.string.str_stairs);
                        tts.ttsClient.play(speech_text);
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_PLAY_SOUND);
                        Log.i(TAG, "계단");
                    }
                }
            });
        }

        @JavascriptInterface
        public void BlueNumber(final int number) {       // 신호등 숫자
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView textView = findViewById(R.id.tv_speech);
                    textView.setText("BlueNumber");

                    if (!tts.ttsClient.isPlaying()) {
                        if (flag_traffic.equals("NUMBER")) {
                            speech_text = Integer.toString(number);
                            button.setEnabled(false);
                            tts.ttsClient.play(speech_text);
                        } else if (flag_traffic.equals("-1") || flag_traffic.equals("RED")) {
                            flag_traffic = "NUMBER";
                            button.setEnabled(false);
                            speech_text = getString(R.string.str_redlight);
                            tts.ttsClient.play(speech_text);
                        }
                            Log.i(TAG, Integer.toString(number));
                        if (number == 1) flag_traffic = "RED";
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_PLAY_SOUND);
                    }
                }
            });
        }

        @JavascriptInterface
        public void BlueLightOn() {       // 신호등 초록불
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView textView = findViewById(R.id.tv_speech);
                    textView.setText("BlueLight");

                    if ((!tts.ttsClient.isPlaying()) && (flag_traffic.equals("-1") || flag_traffic.equals("RED"))) {
                        button.setEnabled(false);
                        speech_text = getString(R.string.str_bluelight);
                        tts.ttsClient.play(speech_text);
                        flag_traffic = "GREEN";
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_PLAY_SOUND);
                        Log.i(TAG, "FLAG : " + flag_traffic);
                        mWebView.loadUrl("javascript:initialize()");
                    }
                }
            });
        }

        @JavascriptInterface
        public void isRedLightisOn() {       // 신호등 빨간불
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView textView = findViewById(R.id.tv_speech);
                    textView.setText("RedLight");

                    if (!tts.ttsClient.isPlaying()) {
                        if (flag_traffic.equals("-1")) {
                            button.setEnabled(false);
                            speech_text = getString(R.string.str_redlight);
                            tts.ttsClient.play(speech_text);
                        }
                        flag_traffic = "RED";
                        Log.i(TAG, "FLAG : " + flag_traffic);
                    }
                }
            });
        }

        @JavascriptInterface
        public String getState() {
            return state_text;
        }
    }

    /***************************************************************************************************************************/
    /***************************************************************************************************************************/
    /***************************************************************************************************************************/

    class STT extends Activity implements SpeechRecognizeListener {
        protected SpeechRecognizerClient client;
        protected SpeechRecognizerClient.Builder builder;
        private static final String TAG = "STT";


        public STT() {
            // 클라이언트 생성
            builder = new SpeechRecognizerClient.Builder().setServiceType(SpeechRecognizerClient.SERVICE_TYPE_WEB);
            client = builder.build();
            client.setSpeechRecognizeListener(this);
        }

        // 음성인식한 결과
        @Override
        public void onResults(Bundle results) {     // 음성 입력이 종료된것으로 판단하고 서버에 질의를 모두 마치고 나면 호출
            final StringBuilder builder = new StringBuilder();

            final ArrayList<String> texts = results.getStringArrayList(SpeechRecognizerClient.KEY_RECOGNITION_RESULTS);
            ArrayList<Integer> confs = results.getIntegerArrayList(SpeechRecognizerClient.KEY_CONFIDENCE_VALUES);

            Log.d(TAG, "Result: " + texts);

            for (int i = 0; i < texts.size(); i++) {
                builder.append(texts.get(i));
                builder.append(" (");
                builder.append(confs.get(i).intValue());
                builder.append(")\n");
            }

            //모든 콜백함수들은 백그라운드에서 돌고 있기 때문에 메인 UI를 변경할려면 runOnUiThread를 사용해야 한다.
            final Activity activity = this;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (activity.isFinishing()) return;
                    textView.setText(texts.get(0));

                    if (state_text.equals(SEARCH)) {  // 목적지를 말할 때는 넘어감
                        state_text = REASK;
                        return;
                    }

                    if (textView.getText().equals("응") || textView.getText().equals("네") || textView.getText().equals("예")) {
                        switch (state_text) {
                            case REASK_:
                                state_text = REASK_YES;
                                break;
                            case RESTART_:
                                state_text = EXPLANATION;
                                break;
                            case PREV_DESTINATION_:
                                state_text = REASK_YES;
                                destination = prev_destination;
                                break;
                        }
                    } else if (textView.getText().equals("아니") || textView.getText().equals("아니요")) {
                        switch (state_text) {
                            case REASK_:
                                state_text = REASK_NO;
                                break;
                            case RESTART_:
                                state_text = NAVIGATE;
                                break;
                            case PREV_DESTINATION_:
                                state_text = EXPLANATION;
                                break;
                        }
                    } else {
                        prev_state = state_text;
                        state_text = RERECORD;
                    }
                }
            });
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            SpeechRecognizerManager.getInstance().finalizeLibrary();
        }

        @Override
        public void onReady() {//모든 하드웨어및 오디오 서비스가 모두 준비 된 다음 호출
            Log.d(TAG, "모든 준비가 완료 되었습니다.");
        }

        @Override
        public void onBeginningOfSpeech() { //사용자가 말하기 시작하는 순간 호출
            Log.d(TAG, "말하기 시작 했습니다.");
        }

        @Override
        public void onEndOfSpeech() {//사용자가 말하기를 끝냈다고 판단되면 호출
            Log.d(TAG, "말하기가 끝났습니다.");
        }

        @Override
        public void onFinished() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    button.setEnabled(true);
                }
            });
            if (!flag_traffic.equals("NUMBER"))
                vibrator.vibrate(VibrationEffect.createOneShot(VIBRATESECONDS, AMPLITUDE));
        }

        @Override
        public void onError(int errorCode, String errorMsg) {
            String errorText = null;
            switch (errorCode) {
                case SpeechRecognizerClient.ERROR_AUDIO_FAIL:
                    errorText = "마이크 접근 불가";
                    break;
                case SpeechRecognizerClient.ERROR_AUTH_FAIL:
                    errorText = "apikey 인증 실패";
                    break;
                case SpeechRecognizerClient.ERROR_CLIENT:
                    errorText = "클라이언트 내부 로직 오류";
                    break;
                case SpeechRecognizerClient.ERROR_NETWORK_FAIL:
                    errorText = "네트워크 오류";
                    break;
                case SpeechRecognizerClient.ERROR_NETWORK_TIMEOUT:
                    errorText = "네트워크 타임아웃";
                    break;
                case SpeechRecognizerClient.ERROR_NO_RESULT:
                    errorText = "인식된 결과가 없음";
                    break;
                case SpeechRecognizerClient.ERROR_RECOGNITION_TIMEOUT:
                    errorText = "전체 소요시간 타임아웃";
                    break;
                case SpeechRecognizerClient.ERROR_SERVER_ALLOWED_REQUESTS_EXCESS:
                    errorText = "요청 허용 횟수 초과";
                    break;
                case SpeechRecognizerClient.ERROR_SERVER_FAIL:
                    errorText = "서버 오류 발생";
                    break;
                case SpeechRecognizerClient.ERROR_SERVER_TIMEOUT:
                    errorText = "서버 응답 시간 초과";
                    break;
                case SpeechRecognizerClient.ERROR_SERVER_UNSUPPORT_SERVICE:
                    errorText = "제공되지 않는 서비스 타입";
                    break;
                case SpeechRecognizerClient.ERROR_SERVER_USERDICT_EMPTY:
                    errorText = "입력된 사용자 사전에 내용이 없음";
                    break;
            }
            final String statusMessage = errorText + " (" + errorCode + ")";
            Log.i(TAG, statusMessage);


            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    button.setEnabled(true);
                    prev_state = state_text;
                    state_text = RERECORD;
                    vibrator.vibrate(VibrationEffect.createOneShot(VIBRATESECONDS, AMPLITUDE));
                }
            });
        }

        @Override
        public void onPartialResult(String partialResult) {// 인식된 음성 데이터를 문자열로 알려 준다.

        }

        @Override
        public void onAudioLevel(float audioLevel) {

        }
    }


    /***************************************************************************************************************************/
    /***************************************************************************************************************************/
    /***************************************************************************************************************************/

    class TTS extends Activity implements TextToSpeechListener {
        protected TextToSpeechClient ttsClient;
        private static final String TAG = "TTS";

        public TTS() {
            ttsClient = new TextToSpeechClient.Builder()
                    .setSpeechMode(TextToSpeechClient.NEWTONE_TALK_1)     // 음성합성방식
                    .setSpeechSpeed(1.0)            // 발음 속도(0.5~4.0)
                    .setSpeechVoice(TextToSpeechClient.VOICE_WOMAN_READ_CALM)  //TTS 음색 모드 설정(여성 차분한 낭독체)
                    .setListener(this)
                    .build();
        }

        @Override
        public void onFinished() { //음성합성이 종료될 때 호출된다.
            int intSentSize = ttsClient.getSentDataSize();      //세션 중에 전송한 데이터 사이즈
            int intRecvSize = ttsClient.getReceivedDataSize();  //세션 중에 전송받은 데이터 사이즈

            final String strInacctiveText = "handleFinished() SentSize : " + intSentSize + "  RecvSize : " + intRecvSize;

            Log.i(TAG, strInacctiveText);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    button.setEnabled(true);
                    vibrator.vibrate(VibrationEffect.createOneShot(VIBRATESECONDS, AMPLITUDE));

                    if (state_text == NAVIGATE) {
                        if (flag_traffic.equals("GREEN"))
                            textView.setText(destination);
                        mWebView.loadUrl("javascript:sendmsg()");// js의 함수 sendmsg() : ros로 msg를 보내기
                    }


                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_PLAY_SOUND);
                    Log.i(TAG, "빨간불");
                }
            });

        }

        // 더 이상 쓰지 않는 경우에는 다음과 같이 해제
        @Override
        public void onDestroy() {
            super.onDestroy();
            TextToSpeechManager.getInstance().finalizeLibrary();
        }

        @Override
        public void onError(int code, String message) { //에러처리
            handleError(code);
        }

        private void handleError(int errorCode) {
            String errorText;
            switch (errorCode) {
                case TextToSpeechClient.ERROR_NETWORK:
                    errorText = "네트워크 오류";
                    break;
                case TextToSpeechClient.ERROR_NETWORK_TIMEOUT:
                    errorText = "네트워크 지연";
                    break;
                case TextToSpeechClient.ERROR_CLIENT_INETRNAL:
                    errorText = "음성합성 클라이언트 내부 오류";
                    break;
                case TextToSpeechClient.ERROR_SERVER_INTERNAL:
                    errorText = "음성합성 서버 내부 오류";
                    break;
                case TextToSpeechClient.ERROR_SERVER_TIMEOUT:
                    errorText = "음성합성 서버 최대 접속시간 초과";
                    break;
                case TextToSpeechClient.ERROR_SERVER_AUTHENTICATION:
                    errorText = "음성합성 인증 실패";
                    break;
                case TextToSpeechClient.ERROR_SERVER_SPEECH_TEXT_BAD:
                    errorText = "음성합성 텍스트 오류";
                    break;
                case TextToSpeechClient.ERROR_SERVER_SPEECH_TEXT_EXCESS:
                    errorText = "음성합성 텍스트 허용 길이 초과";
                    break;
                case TextToSpeechClient.ERROR_SERVER_UNSUPPORTED_SERVICE:
                    errorText = "음성합성 서비스 모드 오류";
                    break;
                case TextToSpeechClient.ERROR_SERVER_ALLOWED_REQUESTS_EXCESS:
                    errorText = "허용 횟수 초과";
                    break;
                default:
                    errorText = "정의하지 않은 오류";
                    break;
            }
            final String statusMessage = errorText + " (" + errorCode + ")";
            Log.i(TAG, statusMessage);
        }
    }

}
