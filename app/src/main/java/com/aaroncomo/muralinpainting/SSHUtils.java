package com.aaroncomo.muralinpainting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Vector;
import ch.ethz.ssh2.*;


public class SSHUtils {
    private static final String ip = "120.78.130.95";
    private static final String usr = "root";
    private static final String passwd = "";
    private static Connection conn;
    private static Object[] output;    // exec函数的返回结果

    SSHUtils (){
        if (!login()) {
            System.out.println("Login failed.");
        }
        output = new Object[512];
    }

    public boolean login(){
        conn = new Connection(ip);
        try {
            //连接远程服务器
            conn.connect();
            //使用用户名和密码登录
            return conn.authenticateWithPassword(usr, passwd);
        } catch (IOException e) {
            System.err.printf("用户%s密码%s登录服务器%s失败！", usr, passwd, ip);
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 执行脚本
     * @param cmd 要在linux上执行的指令
     */
    public void exec(String cmd) {
        InputStream stdOut = null;
        InputStream stdErr = null;
        int ret = -1;
        try {
            //在connection中打开一个新的会话
            Session session = conn.openSession();
            //在远程服务器上执行linux指令
            session.execCommand(cmd);
            //指令执行结束后的输出
            stdOut = new StreamGobbler(session.getStdout());
            BufferedReader br = new BufferedReader(new InputStreamReader(stdOut));
            output = br.lines().toArray();
            //指令执行结束后的错误
            stdErr = new StreamGobbler(session.getStderr());
            br = new BufferedReader(new InputStreamReader(stdErr));
            Object[] err = br.lines().toArray();
            if (err.length != 0) {
                System.out.println("exec error: ".concat(Arrays.toString(err)));
            }
            //取得指令执行结束后的状态
            ret = (int) session.getExitStatus();
            System.out.println("exec ret: " + ret);
            session.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 在远程Linux服务器上，在指定目录下，获取文件各个属性
     * @param remotePath 远程主机的指定目录
     */
    public void getFileProperties(String remotePath){
        try {
            SFTPv3Client sft = new SFTPv3Client(conn);
            Vector<?> v = sft.ls(remotePath);

            for(int i=0;i<v.size();i++){
                SFTPv3DirectoryEntry s = new SFTPv3DirectoryEntry();
                s = (SFTPv3DirectoryEntry) v.get(i);
                //文件名
                String filename = s.filename;
                //文件的大小
                Long fileSize = s.attributes.size;
            }
            sft.close();
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }

    /**
     * 下载服务器文件到本地目录
     * @param remoteFilePath 服务器文件路径
     * @param localDir 本地下载目录
     */
    public void download(String remoteFilePath, String localDir) {
        SCPClient sc = new SCPClient(conn);
        try {
            sc.get(remoteFilePath, localDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Object[] getOutput() {
        return output;
    }

    public int getReturnLength() {
        return output.length;
    }

    public void closeConnection() {
        conn.close();
    }

    public void clean() {
        output = new Object[0];
    }

    public String getFile() {
        return output[0].toString();
    }

    public void log() {
        System.out.println(Arrays.toString(output));
    }
}
