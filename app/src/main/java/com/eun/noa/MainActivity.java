package com.eun.noa;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
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
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;




import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;


import java.util.ArrayList;


public class MainActivity extends Activity implements TextToSpeechListener, SpeechRecognizeListener {
    // 뉴톤/톡 api 변수
    private TextToSpeechClient ttsClient;
    private SpeechRecognizerClient client;
    private SpeechRecognizerClient.Builder builder;
    private static final int REQUEST_CODE_AUDIO_AND_WRITE_EXTERNAL_STORAGE = 0;

    // 레이아웃 변수
    private ImageButton button;
    private TextView textView;
    private WebView mWebView;
    private Button reloadbutton;

    private long backKeyPressedTime;                    // 앱종료 위한 백버튼 누른시간
    private static final String TAG = "MainActivity";   // 로그에 사용
    private String speech_text;                         // 음성인식한 단어 저장
    private String state_text = null;                   // 음성 안내 상태 저장 변수 -> 밑의 변수랑 같이 이용
    private String destination;

    // 음성 안내 순서를 알기 위한 string 변수
    private static final String EXPLANATION = "EXPLANATION";
    private static final String SEARCH = "SEARCH";
    private static final String REASK = "REASK";
    private static final String NAVIGATE = "NAVIGATE";
    private static final String REASK_ANSWER = "REASK_ANSWER";
    private static final String RESTART = "RESTART";
    private static final String YES_REASK = "YES";
    private static final String NO_REASK = "NO";
    private static final String ARRIVAL = "ARRIVAL";
    private static final String PREVIOUS = "PREVIOUS";


    private static final String FILE_NAME = "destination.txt";

    EditText mEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 레이아웃 변수 설정
        mWebView = (WebView) findViewById(R.id.webView);
        button = findViewById(R.id.bt);
        reloadbutton = findViewById(R.id.bt_reload);
        textView = findViewById(R.id.tv);
        mEditText=findViewById(R.id.edit_text);

        // 음성인식을 하는데 필요한 권한 묻기
        // 마이크, 인터넷 권한 필요
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE_AUDIO_AND_WRITE_EXTERNAL_STORAGE);
        }


        // 음성합성 초기화
        SpeechRecognizerManager.getInstance().initializeLibrary(this);
        TextToSpeechManager.getInstance().initializeLibrary(getApplicationContext());

        // 음성인식 초기화
        SpeechRecognizerManager.getInstance().initializeLibrary(this);
        builder = new SpeechRecognizerClient.Builder().setServiceType(SpeechRecognizerClient.SERVICE_TYPE_WEB);

        // 웹뷰 설정
        mWebView.setWebViewClient(new WebViewClient());                 // 웹뷰 클라이언트
        mWebView.setWebChromeClient(new WebChromeClient());             // 웹뷰 크롬 클라이언트
        mWebView.getSettings().setJavaScriptEnabled(true);              // 웹뷰에서 자바스크립트 사용 가능하게
        mWebView.loadUrl("http://192.168.1.148:8080/ros_js.html");      // 서버에 있는 html 파일
        mWebView.setWebContentsDebuggingEnabled(true);                  // 크롬에서 웹뷰 디버깅 가능하게
        mWebView.addJavascriptInterface(new WebBridge(), "NOA");  // js에서 안드로이드 함수를 쓰기 위한 브릿지 설정 -> window.NOA.functionname();


        // 음성합성 객체 설정
        ttsClient = new TextToSpeechClient.Builder()
                .setSpeechMode(TextToSpeechClient.NEWTONE_TALK_1)     // 음성합성방식
                .setSpeechSpeed(1.0)            // 발음 속도(0.5~4.0)
                .setSpeechVoice(TextToSpeechClient.VOICE_WOMAN_READ_CALM)  //TTS 음색 모드 설정(여성 차분한 낭독체)
                .setListener(MainActivity.this)
                .build();


        // 앱 켰을 때 시작
        if (state_text == null) {
            speech_text = getString(R.string.str_start);
            state_text = new String(EXPLANATION);
            //ttsClient.play(speech_text);
        }

        // rosbridge랑 연결이 끊겼을 경우 html 다시 로드하는 버튼
        reloadbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mWebView.reload();
            }
        });








        // 이전 목적
        button.setOnLongClickListener(new View.OnLongClickListener()
        {

            @Override
            public boolean onLongClick(View v) {

                ttsClient = new TextToSpeechClient.Builder()
                        .setSpeechMode(TextToSpeechClient.NEWTONE_TALK_1)     // 음성합성방식
                        .setSpeechSpeed(1.0)            // 발음 속도(0.5~4.0)
                        .setSpeechVoice(TextToSpeechClient.VOICE_WOMAN_DIALOG_BRIGHT)  //TTS 음색 모드 설정(여성 차분한 낭독체)
                        .setListener(MainActivity.this)
                        .build();

                FileInputStream fis = null;
                try {
                    fis = openFileInput(FILE_NAME);
                    InputStreamReader isr = new InputStreamReader(fis);
                    BufferedReader br = new BufferedReader(isr);
                    StringBuilder sb=new StringBuilder();
                    String text;

                    while((text = br.readLine()) != null){
                        sb.append(text).append("\n");
                    }

                    mEditText.setText(sb.toString());
                    ttsClient.play("이전 목적지는" + mEditText.getText() + "입니다.");

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e){
                    e.printStackTrace();
                } finally{
                    if(fis != null){
                        try{
                            fis.close();
                        } catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }

                return true;

            }

        });

        // 음성인식하는 버튼
        button.setOnClickListener(new View.OnClickListener() {


            @Override
            public void onClick(View v) {

//                mWebView.loadUrl("javascript:setflag('학교')");
//                mWebView.loadUrl("javascript:sendmsg()");

                // 음성인식 클라이언트 초기화
                ttsClient = new TextToSpeechClient.Builder()
                        .setSpeechMode(TextToSpeechClient.NEWTONE_TALK_1)     // 음성합성방식
                        .setSpeechSpeed(1.0)            // 발음 속도(0.5~4.0)
                        .setSpeechVoice(TextToSpeechClient.VOICE_WOMAN_READ_CALM)  //TTS 음색 모드 설정(여성 차분한 낭독체)
                        .setListener(MainActivity.this)
                        .build();


                if (state_text.equals(EXPLANATION)) {   // 목적지 묻기
                    // 음성 합성할 문장으로 speech_text 설정
                    speech_text = getString(R.string.str_definition);
                    // 검색을 하기 위해 state_text 설정
                    state_text = SEARCH;
                    // 음성 합성
                    ttsClient.play(speech_text);

                } else if (state_text.equals(SEARCH)) {    // 검색하기
                    // 음성 인식을 위한 변수 빌드
                    client = builder.build();

                    // 음성 인식 시작할 때 나타나는 토스트 메세지
                    Toast.makeText(MainActivity.this, "음성인식을 시작합니다.", Toast.LENGTH_SHORT).show();

                    // 음성 인식
                    client.setSpeechRecognizeListener(MainActivity.this);
                    client.startRecording(true);

                } else if (state_text.equals(REASK)) {      // 인식한 목적지가 맞는지 확인하기
                    speech_text = textView.getText().toString();
                    destination = speech_text;
                    state_text = REASK_ANSWER;
                    ttsClient.play("목적지가 " + speech_text + "입니까?");





                } else if (state_text.equals(REASK_ANSWER)) {       // 목적지 재확인 시 대답
                    client = builder.build();

                    Toast.makeText(MainActivity.this, "음성인식을 시작합니다.", Toast.LENGTH_SHORT).show();

                    client.setSpeechRecognizeListener(MainActivity.this);
                    client.startRecording(true);

                } else if (state_text.equals(NO_REASK)) {       // 목적지가 잘못 인식되었을 경우
                    speech_text = getString(R.string.str_no);
                    state_text = SEARCH;
                    ttsClient.play(speech_text);

                } else if (state_text.equals(YES_REASK)) {      // 목적지가 잘 인식되었을 경우
                    speech_text = getString(R.string.str_navigate);
                    // js의 함수 setflag() : 목적지 이름을 변수에 저장
                    mWebView.loadUrl("javascript:setflag('" + destination + "')");
                    // js의 함수 sendmsg() : ros로 msg를 보내기
                    mWebView.loadUrl("javascript:sendmsg()");
                    state_text = NAVIGATE;
                    ttsClient.play(speech_text);



                    String text = destination;
                    FileOutputStream fos = null;

                    try {
                        fos = openFileOutput(FILE_NAME, MODE_PRIVATE);
                        fos.write(text.getBytes());
                        Toast.makeText(MainActivity.this, "Saved to " + getFilesDir() + "/" + FILE_NAME, Toast.LENGTH_LONG).show();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if(fos != null){
                            try {
                                fos.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }



                } else if (state_text.equals(NAVIGATE)) {       // 안내하기
                    speech_text = getString(R.string.str_restart);
                    state_text = RESTART;
                    ttsClient.play(speech_text);

                } else if (state_text.equals(RESTART)) {        // 목적지를 다시 설정할 경우
                    client = builder.build();

                    Toast.makeText(MainActivity.this, "음성인식을 시작합니다.", Toast.LENGTH_SHORT).show();

                    client.setSpeechRecognizeListener(MainActivity.this);
                    client.startRecording(true);

                } else if (state_text.equals(ARRIVAL)) {        // 도착했을 경우
                    speech_text = getString(R.string.str_ask2);
                    state_text = EXPLANATION;
                    mWebView.loadUrl("javascript:sendmsg()");
                    ttsClient.play(speech_text);
                }
            }
        });

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
    public void onError(int errorCode, String errorMsg) {

    }

    @Override
    public void onPartialResult(String partialResult) {// 인식된 음성 데이터를 문자열로 알려 준다.

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

                TextView textView = findViewById(R.id.tv);
                textView.setText(texts.get(0));

                if (state_text.equals(SEARCH))      // 목적지를 말할 때는 넘어감
                    state_text = REASK;
                else if (state_text.equals(REASK_ANSWER)) { // 목적지가 올바르게 인식되었는지 확인할 때
                    if (textView.getText().equals("응") || textView.getText().equals("네") || textView.getText().equals("예"))
                        state_text = YES_REASK;
                    else
                        state_text = NO_REASK;
                } else if (state_text.equals(RESTART)) {    // 목적지를 다시 설정할 때
                    if (textView.getText().equals("응") || textView.getText().equals("네") || textView.getText().equals("예"))
                        // 맞으면 처음으로
                        state_text = EXPLANATION;
                    else
                        // 아니면 그대로
                        state_text = NAVIGATE;
                }
            }
        });
    }

    @Override
    public void onAudioLevel(float audioLevel) {

    }

    @Override
    public void onFinished() {
        int intSentSize = ttsClient.getSentDataSize();
        int intRecvSize = ttsClient.getReceivedDataSize();

        final String strInacctiveText = "onFinished() SentSize : " + intSentSize + " RecvSize : " + intRecvSize;

        Log.i(TAG, strInacctiveText);

        ttsClient = null;
    }

    // 자바스크립트에서 안드로이드 함수 호출할 때 사용
    // 자바스크립트 코드 : window.NOA.함수이름()
    class WebBridge {
        @JavascriptInterface
        public void SuccessArrival() {  // 목적지에 도착할 경우 실행
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView textView = findViewById(R.id.tv);
                    textView.setText("Success Arrival");

                    state_text = ARRIVAL;
                    ttsClient = new TextToSpeechClient.Builder()
                            .setSpeechMode(TextToSpeechClient.NEWTONE_TALK_1)     // 음성합성방식
                            .setSpeechSpeed(1.0)            // 발음 속도(0.5~4.0)
                            .setSpeechVoice(TextToSpeechClient.VOICE_WOMAN_READ_CALM)  //TTS 음색 모드 설정(여성 차분한 낭독체)
                            .setListener(MainActivity.this)
                            .build();

                    // 다시 처음으로
                    speech_text = getString(R.string.str_arrival);
                    state_text = EXPLANATION;
                    ttsClient.play(speech_text);

                }
            });
        }

        @JavascriptInterface
        public void thereIsStairs() {       // 계단 인식할 경우 실행
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView textView = findViewById(R.id.tv);
                    textView.setText("There are Stairs");

                    ttsClient = new TextToSpeechClient.Builder()
                            .setSpeechMode(TextToSpeechClient.NEWTONE_TALK_1)     // 음성합성방식
                            .setSpeechSpeed(1.0)            // 발음 속도(0.5~4.0)
                            .setSpeechVoice(TextToSpeechClient.VOICE_WOMAN_READ_CALM)  //TTS 음색 모드 설정(여성 차분한 낭독체)
                            .setListener(MainActivity.this)
                            .build();

                    speech_text = getString(R.string.str_stairs);
                    ttsClient.play(speech_text);
                }
            });
        }
    }

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

}
