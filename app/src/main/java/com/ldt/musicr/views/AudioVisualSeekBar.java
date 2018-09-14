package com.ldt.musicr.views;import android.animation.ValueAnimator;import android.content.Context;import android.graphics.Canvas;import android.graphics.Color;import android.graphics.LinearGradient;import android.graphics.Paint;import android.graphics.Shader;import android.media.MediaPlayer;import android.os.AsyncTask;import android.os.Handler;import android.support.annotation.NonNull;import android.support.annotation.Nullable;import android.util.AttributeSet;import android.util.Log;import android.view.GestureDetector;import android.view.MotionEvent;import android.view.View;import com.ldt.musicr.InternalTools.Animation;import com.ldt.musicr.InternalTools.MCoordinate.MPoint;import com.ldt.musicr.InternalTools.Tool;import com.ldt.musicr.services.ITimberService;import com.ldt.musicr.views.soundfile.CheapSoundFile;import java.io.File;/** * Created by trung on 09:05 AM 24 Jan 2018. */public class AudioVisualSeekBar extends View {    public static final String TAG = "AudioVisualSeekBar";    private double NumberOfFrameInAPen;    protected boolean mInitialized;    protected float range;    protected float scaleFactor;    protected float minGain;    public interface AudioVisualSeekBarListener {        void currentTime(int ms);    }    public AudioVisualSeekBar(Context context) {        super(context);        init(context);    }    public AudioVisualSeekBar(Context context, @Nullable AttributeSet attrs) {        super(context, attrs);        init(context);    }    public AudioVisualSeekBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {        super(context, attrs, defStyleAttr);        init(context);    }    private float amount_hide_paint = 0.25f;    private float amount_translucent_hide_paint = 0.15f;    private void init(Context context) {        // do the initialization here        oneDp = Tool.getOneDps(getContext());        mFileName = "";        updateState(Command.BEGIN);        mHandler = new Handler();        mHandler.postDelayed(mTimerRunnable, (long) nextDelayedTime);        mActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);        mActivePaint.setStyle(Paint.Style.FILL_AND_STROKE);        mActivePaint.setStrokeWidth(3 * oneDp);        mTranslucentActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);        mTranslucentActivePaint.setStyle(Paint.Style.FILL_AND_STROKE);        mTranslucentActivePaint.setStrokeWidth(3 * oneDp);        mHidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);        mHidePaint.setStyle(Paint.Style.FILL_AND_STROKE);        mHidePaint.setStrokeWidth(3 * oneDp);        mTranslucentHidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);        mTranslucentHidePaint.setStyle(Paint.Style.FILL_AND_STROKE);        mTranslucentHidePaint.setStrokeWidth(3 * oneDp);        mGestureDetector = new GestureDetector(                context,                new GestureDetector.SimpleOnGestureListener() {                    public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {                        waveformFling(vx);                        return true;                    }                });    }    protected boolean mIsTouchDragging;    protected int mOffset;    protected int mOffsetGoal;    protected int mFlingVelocity;    protected void waveformFling(float vx) {        mActivePaint.setAlpha(255);        mTranslucentActivePaint.setAlpha((int) (amount_translucent_paint*255));        mIsTouchDragging = false;       // mOffsetGoal = mOffset;        mFlingVelocity = (int) (vx);        doFlingTransition();    }    protected float mTouchDown;    protected int mTouchInitialOffset;    protected long mWaveformTouchStartMsec;    protected float tempCurrentWavePos = 0;    void waveformTouchDown(float x) {        mIsTouchDragging = true; // ngón tay đang nhấn vào màn hình        mTouchDown = x; // ghi lại vị trí nhấn        //mTouchInitialOffset = mOffset;        tempCurrentWavePos = currentWavePos; // ghi lại vị trí line hiện tại vào biến tạm        mFlingVelocity = 0; // set lại vận tốc        mWaveformTouchStartMsec = System.currentTimeMillis(); // ghi lại thời gian để kiểm tra click khi hết nhấn        /*         *         */        mActivePaint.setAlpha((int) ((1-touchDown_alphaAdd)*255));        mTranslucentActivePaint.setAlpha((int) ((amount_translucent_paint-touchDown_alphaAdd)*255));    }    void waveformTouchMove(float x) {        //   mOffset = trap((int) (mTouchInitialOffset + (mTouchDown - x)));        //      Log.d (TAG,"onTouchMove : x = "+x+", mOffset = "+mOffset);        //  updateDisplay();        if(runningTrailer&&va!=null&&va.isRunning()) va.cancel();        float deltaX = x - mTouchDown;        calculateAndDrawWaveform(tempCurrentWavePos - deltaX);    }    void waveformTouchUp() {        mActivePaint.setAlpha(255);        mTranslucentActivePaint.setAlpha((int) (amount_translucent_paint*255));        mActivePaint.setAlpha(255);        mIsTouchDragging = false;        //mOffsetGoal = mOffset; // ?        //updateDisplay();        long elapsedMsec = System.currentTimeMillis() - mWaveformTouchStartMsec;        runningTrailer = false;        if (elapsedMsec < 300) { // A Quick Touch - A Click            Log.d(TAG, "Elapsed");            runTrailer();            /*            if (mIsPlaying) {                int seekMsec = mWaveformView.pixelsToMillisecs((int) (mTouchDown + mOffset));                if (seekMsec >= mPlayStartMsec && seekMsec < mPlayEndMsec) {                    mPlayer.seekTo(seekMsec - mPlayStartOffset);                } else {                    handlePause();                }            } else {                onPlay((int) (mTouchDown + mOffset));            }            */        }    }    Handler FlingHandler = new Handler();    private long timeBegin = 0;    private long timeFling = 0;    MediaPlayer mediaPlayer;    private Runnable FlingRunnable = new Runnable() {        @Override        public void run() {            timeFling = System.currentTimeMillis() - timeBegin;            int delta = (int) ((mFlingVelocity + 0.0f) / (60)); // khung hình tiếp theo sẽ di chuyển từng này            if (mFlingVelocity > delta) {  // ?                mFlingVelocity -= delta;            } else if (mFlingVelocity < -delta) {                mFlingVelocity -= delta;            } else {                mFlingVelocity = 0;            }            calculateAndDrawWaveform(currentWavePos - delta);            if (mFlingVelocity != 0) {                isFlingTransiting = true;                FlingHandler.post(FlingRunnable);            }            else isFlingTransiting = false;        }    };    private boolean isFlingTransiting = false;    protected void doFlingTransition() {        if (isFlingTransiting) {            // Vào đây nghĩa là 1 fling đang chồng lên mà fling trước chưa chạy xong            //  Ta sẽ huỷ fling cũ đi để chạy fling mới.        }        // bắt đầu một fling mới        timeBegin = System.currentTimeMillis();        isFlingTransiting = true;        FlingHandler.post(FlingRunnable);    }    /**     * Call this to redraw the wave form     *     * @param CurrentWavePos the position of the scroller in pixel unit     */    protected void calculateAndDrawWaveform(float CurrentWavePos) {        if (CurrentWavePos > ruler) CurrentWavePos = ruler;        else if (CurrentWavePos < 0) CurrentWavePos = 0;        currentWavePos = CurrentWavePos; // ?        translateX = (int) (mSeekBarCenter.X - currentWavePos);        float percentage = currentWavePos / ruler;        if (percentage < 0) percentage = 0;        else if (percentage > 1) percentage = 1;        lineFrom = (int) (percentage * TotalPens - NumberPensAppearInScreen / 2.0f - 10);        lineTo = (int) (percentage * TotalPens + NumberPensAppearInScreen / 2.0f + 10);        if (lineFrom < 0) lineFrom = 0;        if (lineTo > TotalPens) lineTo = TotalPens;        //Log.d(TAG, "percent = " + percentage + ", lineFrom = " + lineFrom + ", lineTo = " + lineTo);        invalidate(); // TODO :  should  we redraw all the view?    }    private boolean runningTrailer = false;    ValueAnimator va;    protected void runTrailer() {        runningTrailer = true;        va = ValueAnimator.ofFloat(0,ruler);        va.setInterpolator(Animation.getInterpolator(0));        va.setDuration((long) (mDuration*1000));        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {            @Override            public void onAnimationUpdate(ValueAnimator animation) {                float value = (float) animation.getAnimatedValue();                calculateAndDrawWaveform(value);            }        });        va.start();    }    protected void updateDisplay() {    }    protected float oneDp;    protected int mWidth;    protected int mHeight;    protected MPoint mSeekBarCenter;    protected MPoint mRectCenter;    @Override    protected void onSizeChanged(int w, int h, int oldw, int oldh) {        super.onSizeChanged(w, h, oldw, oldh);        if (initdrawn) {            mWidth = w;            mHeight = h;            lineHeight =  maxLineHeight=  top_bottom_ratio * (mHeight / 2.0f - oneDp);            mSeekBarCenter.X = mWidth / 2;            mSeekBarCenter.Y = mHeight / 2;        }    }    protected Paint mActivePaint;    protected Paint mTranslucentActivePaint;    protected Paint mHidePaint;    protected Paint mTranslucentHidePaint;    public Paint mLinearPaint;    protected double nextDelayedTime = 30;    protected double pp_point_counting = 0;    protected double pp_point = 0;    protected boolean pp_way = true;    protected double pp_timeOneRound = 700.f;    protected final android.view.animation.Interpolator pp_interpolator = Animation.getInterpolator(9);    protected Runnable mTimerRunnable = new Runnable() {        @Override        public void run() {            // the repeater of drawing begins here            //  if(STATE!=State.NOTHING) // until STATE be different from NOTHING, it won't draw again.            calculating();            invalidate();            mHandler.postDelayed(mTimerRunnable, (long                    ) nextDelayedTime);        }    };    protected void updateScreen(Canvas canvas) {        switch (STATE) {            case NOTHING:                onDrawNothing(canvas);                break;            case PREPARING:                onDrawPreparing(canvas);                break;            case VISUALIZING:                onDrawVisualizing(canvas);                break;            case SWITCHING:                onDrawSwitching(canvas);                break;            default:                break;        }    }    protected void calculating() {        switch (STATE) {            case NOTHING:                onCalculateNothing();                break;            case PREPARING:                onCalculatePreparing();                break;            case VISUALIZING:                onCalculateVisualizing();                break;            case SWITCHING:                onCalculateSwitching();                break;            default:                break;        }    }    protected void onCalculateNothing() {    }    protected void reformat() {        switch (STATE) {            case NOTHING:                reformatNothing();                break;            case PREPARING:                reformatPreparing();                break;            case VISUALIZING:                reformatVisualizing();                break;            case SWITCHING:                reformatSwitching();                break;            default:                break;        }    }    protected void reformatPreparing() {        pp_point = 0;        pp_way = true;        pp_point_counting = 0;    }    protected void reformatNothing() {    }    protected void reformatVisualizing() {        currentFractionComplete = 0;    }    protected void reformatSwitching() {    }    protected void onCalculatePreparing() {        if (currentFractionComplete >= 1 && ((pp_point >= 0.5f && pp_way) || (pp_point <= 0.5f && !pp_way)))    // check ending :            updateState(Command.PREPARED_ALREADY);        else { // do work            if (pp_point_counting >= pp_timeOneRound / nextDelayedTime) {                pp_point_counting = 0;                pp_way = !pp_way;            }            pp_point_counting++;            pp_point = pp_point_counting / (pp_timeOneRound / nextDelayedTime);            pp_point = pp_interpolator.getInterpolation((float) pp_point);            if (!pp_way) pp_point = 1 - pp_point;            //  Log.d(TAG,pp_point_counting+" & "+pp_point);        }    }    protected void onCalculateVisualizing() {    }    protected void onCalculateSwitching() {    }    protected float tape = 0.01f;    protected float cross = 0.1f;    protected final int color_linear[] = new int[]{0xff00dbde, 0xfffc00ff};    @NonNull    private LinearGradient getLinearShader(int color_1, int color_2) {        int[] color = new int[]{color_1, color_2, color_2, color_1};        float sum = tape + 2 * cross;        float[] pos = new float[]{0, cross / sum, 1 - cross / sum, 1};        return new LinearGradient(-sum * mWidth / 2, 0, sum * mWidth / 2, 0, color, pos,                Shader.TileMode.CLAMP);    }    protected void onDrawNothing(Canvas canvas) {    }    protected void onDrawPreparing(Canvas canvas) {        // canvas.drawLine(0,(float)mSeekBarCenter.Y,(float) (mWidth*currentFractionComplete),(float)mSeekBarCenter.Y,mActivePaint);        canvas.save();        canvas.translate((float) (pp_point * mWidth), (float) mSeekBarCenter.Y);        canvas.drawLine((float) (-pp_point * mWidth), 0, (float) (mWidth - pp_point * mWidth), 0, mLinearPaint);        canvas.restore();    }    protected void onDrawVisualizing(Canvas canvas) {        drawWave(canvas);    }    public interface SeekBarListener {        boolean onSeekBarSeekTo(float posTime);        void onSeekBarTouchDown();        void onSeekBarTouchUp();    }    /**     * currentWavePos is in range [0;ruler], in pixel unit     */    protected float ruler = 0;    protected float currentTime  = 0;    protected float currentWavePos = 0.0f;    private float touchDownPos = 0;    private float currentTouchPos;    private int touchDownMove = 0;    private int touchDownFling = 0;    private int lineFrom = 0, lineTo = 0;    private int deltaX = 0;    private int translateX = 0;    // tỉ lệ về độ cao của line trên so với height/2    private float top_bottom_ratio = 4/7f;    private float maxLineHeight;    private float lineHeight;    public float getCurrentTimeFromPos( float currentPos) {        return 0;    }    /**     * called by {@link #onDrawVisualizing};     *     * @param canvas the canvas of view.     */    protected void drawWave(Canvas canvas) {        canvas.translate(translateX, (float) 0);        for (int pos = lineFrom; pos < lineTo; pos++) {            if (pos < (lineTo + lineFrom) / 2) {                canvas.drawLine(                        (float) (pos * (thickPen + distancePen)),                        (float) (mSeekBarCenter.Y - oneDp),                        (float) (pos * (thickPen + distancePen)),                        (float) (mSeekBarCenter.Y - oneDp -lineHeight* SmoothedPenGain[pos]),                        mActivePaint);                canvas.drawLine(                        (float) (pos * (thickPen + distancePen)),                        (float) (mSeekBarCenter.Y + oneDp),                        (float) (pos * (thickPen + distancePen)),                        (float) (mSeekBarCenter.Y + oneDp + lineHeight * SmoothedPenGain[pos]),                        mTranslucentActivePaint);            }            else {                canvas.drawLine(                        (float) (pos * (thickPen + distancePen)),                        (float) (mSeekBarCenter.Y - oneDp),                        (float) (pos * (thickPen + distancePen)),                        (float) (mSeekBarCenter.Y - oneDp - lineHeight * SmoothedPenGain[pos]),                        mHidePaint);                canvas.drawLine(                        (float) (pos * (thickPen + distancePen)),                        (float) (mSeekBarCenter.Y + oneDp),                        (float) (pos * (thickPen + distancePen)),                        (float) (mSeekBarCenter.Y + oneDp +lineHeight * SmoothedPenGain[pos]),                        mTranslucentHidePaint);            }        }    }    /*        protected float pixelToSeconds(float distancePen) {        }        protected float secondsToPixel(float sec) {        }    */    SeekBarListener listener;    public void setSeekBarListener(SeekBarListener listener) {        this.listener = listener;    }    protected GestureDetector mGestureDetector;    @Override    public boolean onTouchEvent(MotionEvent event) {        performClick();        if (mGestureDetector.onTouchEvent(event)) {            return true;        }        switch (event.getAction()) {            case MotionEvent.ACTION_DOWN:                waveformTouchDown(event.getX());                break;            case MotionEvent.ACTION_MOVE:                waveformTouchMove(event.getX());                break;            case MotionEvent.ACTION_UP:                waveformTouchUp();                break;        }        return true;        /*        switch (event.getAction()) {            case MotionEvent.ACTION_DOWN:                inTouchDown = true;                lastAppliedPos= touchDownPos = event.getRawX();                break;            case MotionEvent.ACTION_UP:                inTouchDown = false;                break;            case MotionEvent.ACTION_MOVE:                inTouchMove = true;                break;            default:                break;        }        currentTouchPos = event.getRawX();        recalculateWaveLine();        return true;        */    }    @Override    public boolean performClick() {        return super.performClick();    }    protected void onDrawSwitching(Canvas canvas) {    }    @Override    protected void onDraw(Canvas canvas) {        super.onDraw(canvas);        initDraw();        updateScreen(canvas);    }    /*        NOTHING: It means that SeekBar now do not show visualization, it is just a normal seek bar.        PREPARING: A Sound File is being parsed by its and will show a visualization when it finishes.        VISUALIZING:  VISUALIZING is being showed        SWITCHING: Close Effect is being showed to prepare for a new sound file.         */    enum State {        NOTHING,        PREPARING,        VISUALIZING,        SWITCHING    }    enum Command {        FILE_SET,        BEGIN,        PREPARED_ALREADY    }    private State STATE;    private void updateState(Command state) {        switch (state) {            case FILE_SET:                if (STATE == State.NOTHING)                    onBeginPreparing();                else if (STATE == State.VISUALIZING)                    onBeginSwitching();                break;            case BEGIN:                STATE = State.NOTHING;                break;            case PREPARED_ALREADY:                onBeginVisualizing();                break;            default:                break;        }    }    protected double currentFractionComplete = 0;    // Only this    // set a new cheap sound file.    void onBeginPreparing() {        STATE = State.PREPARING;        reformat();        final CheapSoundFile.ProgressListener listener = new CheapSoundFile.ProgressListener() {            @Override            public boolean reportProgress(double fractionComplete) {                currentFractionComplete = fractionComplete;                //Log.d(TAG,"frac = "+currentFractionComplete);                return mLoadingKeepGoing;            }        };        new AsyncTask<Void, Void, Void>() {            @Override            protected Void doInBackground(Void... voids) {                try {                    mSoundFile = CheapSoundFile.create(mFile.getAbsolutePath(), listener);                    calculateSound();                } catch (final Exception e) {                    Log.e(TAG, "Error while loading sound file", e);                }                return null;            }            @Override            protected void onPostExecute(Void aVoid) {                super.onPostExecute(aVoid);                Log.d(TAG, "fraction = " + currentFractionComplete);                if (currentFractionComplete != 1) currentFractionComplete = 1;                pp_timeOneRound = 200;            }        }.execute();    }    // Or this    // Remove current cheap sound file and set a new one.    void onBeginSwitching() {        STATE = State.SWITCHING;        reformat();        onBeginPreparing();    }    protected void onBeginVisualizing() {        STATE = State.VISUALIZING;        reformat();    }    Handler waitHandler = new Handler();    Runnable runnable_UpdateState_FileSet = new Runnable() {        @Override        public void run() {            updateState(Command.FILE_SET);        }    };    public void Visualize(String fileName) {        mFileName = fileName;        mFile = new File(mFileName);        updateProperties();        waitHandler.postDelayed(runnable_UpdateState_FileSet, 200);    }    //protected long mLoadingLastUpdateTime = 0;    protected boolean mLoadingKeepGoing = true;    protected String mFileName;    protected File mFile;    protected CheapSoundFile mSoundFile;    protected Handler mHandler;    protected boolean initdrawn = false;    protected double mSampleRate;    protected double mSamplesPerFrame;    protected double mNumFrames;    protected double mDuration;    protected int mIntDuration;    protected int mMaxGain, mMinGain;    protected int[] mFrameGain;    double thickPen;    double distancePen;    int NumberFrameAppearInScreen;    int NumberPensAppearInScreen;    int TotalPens;    double[] SmoothedPenGain;    protected void calculateSound() {        // run in the background        mNumFrames = mSoundFile.getNumFrames();        mSampleRate = mSoundFile.getSampleRate();        mSamplesPerFrame = mSoundFile.getSamplesPerFrame();        mDuration = mNumFrames * mSamplesPerFrame / mSampleRate + 0.0f;        mIntDuration = (int) mDuration;        mFrameGain = mSoundFile.getFrameGains();        mMaxGain = 0;        mMinGain = 255;        for (int i = 0; i < mNumFrames; i++) {            if (mMaxGain < mFrameGain[i]) mMaxGain = mFrameGain[i];            if (mMinGain > mFrameGain[i]) mMinGain = mFrameGain[i];        }        thickPen = 3 * oneDp;        distancePen = oneDp;        //30 s for a screen width        // how many frames appeared in a screen width ?        // how many pens appeared in a screen width ?        // >> how many frame for one pen ?        NumberFrameAppearInScreen = (int) (mNumFrames * 30 / mDuration);        NumberPensAppearInScreen = (int) (((mWidth + distancePen) / (0.0f + oneDp) + 1.0f) / 4.0f);        NumberOfFrameInAPen = NumberFrameAppearInScreen / NumberPensAppearInScreen;        double re = (mNumFrames + 0.0f) / NumberOfFrameInAPen;        TotalPens = (re == ((int) re)) ? (int) re : ((int) re + 1);        double[] originalPenGain = new double[TotalPens];        originalPenGain[0] = 0;        //  reduce the frame gains array (large data) into the pen gains with smaller data.        int iPen = 0;        int pos = 0;        for (int iFrame = 0; iFrame < mNumFrames; iFrame++) {            originalPenGain[iPen] += mFrameGain[iFrame];            pos++;            if (iFrame == mNumFrames - 1) {                originalPenGain[iPen] /= pos;            } else if (pos == NumberOfFrameInAPen) {                originalPenGain[iPen] /= NumberOfFrameInAPen;                pos = 0;                iPen++;            }        }        // make pen gains smoothly        computeDoublesForAllZoomLevels(TotalPens, originalPenGain);        SmoothedPenGain = new double[TotalPens];        for (int i_pen = 0; i_pen < TotalPens; i_pen++)            SmoothedPenGain[i_pen] = getHeight(i_pen, TotalPens, originalPenGain, scaleFactor, minGain, range);        ruler = (float) (TotalPens * thickPen + (TotalPens - 1) * distancePen);        currentWavePos = 0;        lineFrom = 0;        lineTo = NumberPensAppearInScreen / 2 + 10;        translateX = (int) (mSeekBarCenter.X - currentWavePos);        int x=0;    }    ITimberService mService;    protected double getHeight(int i, int totalPens, double[] penGain, float scaleFactor, float minGain, float range) {        double value = (getGain(i, totalPens, penGain) * scaleFactor - minGain) / range;        if (value < 0.0)            value = 0.0f;        if (value > 1.0)            value = 1.0f;        value = (value+0.05)/1.05f;        return value;    }    /**     * Called once when a new sound file is added     */    protected void computeDoublesForAllZoomLevels(int totalPenGains, double[] orginPenGain) {        // Make sure the range is no more than 0 - 255        float maxGain = 1.0f;        for (int i = 0; i < totalPenGains; i++) {            float gain = (float) getGain(i, totalPenGains, orginPenGain);            if (gain > maxGain) {                maxGain = gain;            }        }        scaleFactor = 1.0f;        if (maxGain > 255.0) {            scaleFactor = 255 / maxGain;        }        // Build histogram of 256 bins and figure out the new scaled max        maxGain = 0;        int gainHist[] = new int[256];        for (int i = 0; i < totalPenGains; i++) {            int smoothedGain = (int) (getGain(i, totalPenGains, orginPenGain) * scaleFactor);            if (smoothedGain < 0)                smoothedGain = 0;            if (smoothedGain > 255)                smoothedGain = 255;            if (smoothedGain > maxGain)                maxGain = smoothedGain;            gainHist[smoothedGain]++;        }        // Re-calibrate the min to be 5%        minGain = 0;        int sum = 0;        while (minGain < 255 && sum < totalPenGains / 20) {            sum += gainHist[(int) minGain];            minGain++;        }        // Re-calibrate the max to be 99%        sum = 0;        while (maxGain > 2 && sum < totalPenGains / 100) {            sum += gainHist[(int) maxGain];            maxGain--;        }        range = maxGain - minGain;        mInitialized = true;    }    protected double getGain(int i, int totalPens, double[] penGain) {        int x = Math.min(i, totalPens - 1);        if (totalPens < 2) {            return penGain[x];        } else {            if (x == 0) {                return (penGain[0] / 2.0f) + (penGain[1] / 2.0f);            } else if (x == totalPens - 1) {                return (penGain[totalPens - 2] / 2.0f) + (penGain[totalPens - 1] / 2.0f);            } else {                return (penGain[x - 1] / 3.0f) + (penGain[x] / 3.0f) + (penGain[x + 1] / 3.0f);            }        }    }    /*    protected String secondsToMinutes(String seconds) {    }    */    private final float amount_translucent_paint = 0.588f;    public void updateProperties() {        initDraw();        mActivePaint.setColor(Tool.getSurfaceColor());        mTranslucentActivePaint.setColor(Tool.getSurfaceColor());        mTranslucentActivePaint.setAlpha((int) (amount_translucent_paint*255));        int global = Tool.getGlobalColor();        mLinearPaint.setShader(getLinearShader(global, Tool.getSurfaceColor()));        mHidePaint.setColor(Color.BLACK);        mHidePaint.setAlpha((int) (amount_hide_paint*255));        mTranslucentHidePaint.setColor(Color.BLACK);        mTranslucentHidePaint.setAlpha((int) (amount_translucent_hide_paint*255));    }    private float touchDown_alphaAdd =0.2f;    protected void initDraw() {        if (initdrawn) return;        mWidth = getMeasuredWidth();        mHeight = getMeasuredHeight();        mSeekBarCenter = new MPoint(mWidth / 2, mHeight / 2);        mRectCenter = new MPoint(mWidth/2, mHeight/2);        lineHeight =  maxLineHeight=  top_bottom_ratio * (mHeight / 2.0f - oneDp);        mLinearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);        mLinearPaint.setShader(getLinearShader(Tool.getGlobalColor(), Tool.getSurfaceColor()));        mLinearPaint.setStyle(Paint.Style.FILL_AND_STROKE);        mLinearPaint.setStrokeWidth(3 * oneDp);    }}