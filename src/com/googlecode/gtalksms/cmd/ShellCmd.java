package com.googlecode.gtalksms.cmd;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;

import android.os.Handler;
import android.util.Log;

import com.googlecode.gtalksms.MainService;
import com.googlecode.gtalksms.R;
import com.googlecode.gtalksms.tools.Tools;
import com.googlecode.gtalksms.xmpp.XmppFont;
import com.googlecode.gtalksms.xmpp.XmppMsg;

public class ShellCmd extends CommandHandlerBase {
    Handler _cmdHandler = new Handler();
    Thread _cmdThread;
    StringBuilder _cmdResults = new StringBuilder();
    String _currentCommand;
    XmppFont _font = new XmppFont("consolas", "red");

    
    public ShellCmd(MainService mainService) {
        super(mainService, new String[] {"cmd"}, CommandHandlerBase.TYPE_SYSTEM);
    }
    
    public static boolean askRootAccess() {
        try {
            Process p = Runtime.getRuntime().exec("su");

            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            // TODO issue "id" command an check if result contains "uid=0"
            os.writeBytes("exit\n");
            os.flush();
            p.waitFor();
            if (p.exitValue() != 255) {
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
        
    private Runnable _cmdRunnable = new Runnable() {
        
        public void run() {
            _cmdResults.append(_currentCommand);
            _cmdResults.append(Tools.LineSep);
            
           
            Process myproc = null;
            
            try { 
                if (!askRootAccess()) {
                    _cmdResults.append(_context.getString(R.string.chat_error_root) + Tools.LineSep);
                    myproc = Runtime.getRuntime().exec(new String[] {"/system/bin/sh", "-c", _currentCommand});
                } else {
                    myproc = Runtime.getRuntime().exec("su");

                    DataOutputStream os = new DataOutputStream(myproc.getOutputStream());
                    os.writeBytes(_currentCommand + "\n");
                    os.writeBytes("exit\n");
                    os.flush();
                    os.close();
                }
    
                readStream(myproc.getInputStream());
                readStream(myproc.getErrorStream());
                
                sendResults();
            }
            catch (Exception ex) {
                Log.w(Tools.LOG_TAG, "Shell command error", ex);
            }
            
            _cmdThread = null;
            _currentCommand = null;
        }
        
        void readStream(InputStream is) throws Exception {
            String line;
            Date start = new Date();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            
            while ((line = reader.readLine()) != null) {
                _cmdResults.append(line);
                _cmdResults.append(Tools.LineSep);
                
                Date end = new Date();
                if ((end.getTime() - start.getTime()) / 1000 > 10 || _cmdResults.length() > 5000 ) {
                    start = end;
                    int last = _cmdResults.lastIndexOf("\n");
                    if (last != -1) {
                        XmppMsg msg = new XmppMsg(_font);
                        msg.append(_cmdResults.substring(0, last + 1));
                        send(msg);
                        _cmdResults.delete(0, last + 1);
                    }
                }
            }
        }
    };
    
    @Override
    protected void execute(String unused, String cmd) {
        // check if the previous Command Thread still exists
        if (_cmdThread != null && _cmdThread.isAlive()) {
            send(_currentCommand + " killed.");
            try { 
                _cmdThread.interrupt();
                _cmdThread.join(1000); 
            } catch (Exception e) {}
            
            try { _cmdThread.stop(); } catch (Exception e) {}
            
            sendResults();
        }

        _currentCommand = cmd;
        _cmdThread = new Thread(_cmdRunnable);
        _cmdThread.start();
    }
    
    private void sendResults() {
        XmppMsg msg = new XmppMsg(_font);
        msg.append(_cmdResults.toString());
        send(msg);
        _cmdResults = new StringBuilder();
    }    
    
    @Override
    public String[] help() {
        String[] s = { 
                getString(R.string.chat_help_cmd, makeBold("\"cmd:#command#\""))
                };
        return s;
    }
}
