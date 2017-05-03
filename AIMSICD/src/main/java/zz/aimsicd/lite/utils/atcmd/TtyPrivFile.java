/* Android IMSI-Catcher Detector | (c) AIMSICD Privacy Project
 * -----------------------------------------------------------
 * LICENSE:  http://git.io/vki47 | TERMS:  http://git.io/vki4o
 * -----------------------------------------------------------
 */
package zz.aimsicd.lite.utils.atcmd;

import android.util.Log;

import java.io.IOException;

public class TtyPrivFile extends TtyStream {

    public static final String TAG = "AICDL";
    public static final String mTAG = "XXX";

    protected Process mReadProc;
    protected Process mWriteProc;

    public TtyPrivFile(String ttyPath) throws IOException {
        // TODO robustify su detection?
        this(
            new ProcessBuilder("su", "-c", "\\exec cat <" + ttyPath).start(),
            new ProcessBuilder("su", "-c", "\\exec cat >" + ttyPath).start()
        );
    }

    private TtyPrivFile(Process read, Process write) {
        super(read.getInputStream(), write.getOutputStream());
        mReadProc = read;
        mWriteProc = write;

       Log.d(TAG, mTAG + "mReadProc=" + mReadProc + ", mWriteProc=" + mWriteProc);
    }

    @Override
    public void dispose() {
        super.dispose();
        try {
            // Have to do this to get readproc to exit.
            // I guess it gets blocked waiting for input, so let's give it some.
            mOutputStream.write("ATE0\r".getBytes("ASCII")); // disable local Echo
            mOutputStream.flush();
        } catch (IOException e) {
           Log.e(TAG, mTAG + "moutputstream didnt close", e);
        }
        mReadProc.destroy();
        mWriteProc.destroy();
    }
}
