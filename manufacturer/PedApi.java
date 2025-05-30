//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.vanstone.trans.api;

import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.View;
import com.vanstone.appsdk.client.SdkApi;
import com.vanstone.base.interfaces.PedListener;
import com.vanstone.emvcb.EmvCallBackImpl;
import com.vanstone.page.widget.body.custom.IKeyBoard;
import com.vanstone.page.widget.body.custom.OnKeyBoardClickListener;
import com.vanstone.transex.ped.IGetDukptPinListener;
import com.vanstone.transex.ped.IGetPinResultListenner;
import com.vanstone.utils.ByteUtils;
import com.vanstone.utils.CommonConvert;

public class PedApi {
    private static int g_PublicKeyFlag = 0;
    private static final String TAG = "PEDAPI";
    public static final String PEDPLACE_PUBLIC = "PUBLIC";
    public static final String PEDPLACE_PRIVATE = "PRIVATE";
    public static final int PEDKEYTYPE_MASTKEY = 1;
    public static final int PEDKEYTYPE_WORKKET = 2;
    private static Context content = null;
    private static int KEYTYPE = 1;
    public static int KEYTYPE_MASTERKEY = 1;
    public static int KEYTYPE_MASTERKEY_CIPHER = 2;
    public static int KEYTYPE_TRANSMISSIONKEY = 3;
    public static int KEYTYPE_DUKPT = 4;
    public static int KAPVALUE = 16;
    private static int mOpenFlag = 0;
    private static final int MKEYMAXINDEX = 999;
    private static final int WKEYMAXINDEX = 2999;
    private static final int MKEYMAXINDEX_USE = 989;
    private static final int WKEYMAXINDEX_USE = 2999;
    private static final int MKEY_21_3DES = 990;
    private static final int MKEY_21_SM4 = 991;
    public static final int PED_TLK = 1;
    public static final int PED_TMK = 2;
    public static final int PED_TPK = 3;
    public static final int PED_TAK = 4;
    public static final int PED_TDK = 5;
    public static final int PED_TEK = 6;
    public static final int PED_TTK = 9;
    private static final int DECRYP = 0;
    private static final int ENCRYP = 1;
    private static final int ICBCTEMPINDEX = 101;
    private static final int DES3TEMPINDEX = 16;
    private static final int DESTEMPINDEX = 0;
    private static int gHdOrSoft = 0;
    private static int g_hasSetPrePed = 0;
    public static String DispStr = "";
    private static PedListener g_pedListener;
    public static byte[] Pan = new byte[25];
    private static int inputOfflineResult = -1;

    public PedApi() {
    }

    public static void PEDSetKeyType_Api(int keyType) {
        try {
            SdkApi.getInstance().getPedHandler().PEDSetKeyType_Api(keyType);
        } catch (RemoteException var2) {
            RemoteException e = var2;
            e.printStackTrace();
        }

    }

    public static void PEDStopPin_Api() {
        try {
            SdkApi.getInstance().getPedHandler().PEDStopPin();
        } catch (RemoteException var1) {
            RemoteException e = var1;
            e.printStackTrace();
        }

    }

    /** @deprecated */
    public static void PEDSetContent_Api(Context act) {
    }

    private static int ExtractPAN(byte[] cardno, byte[] pan) {
        int len = false;
        int len = ByteUtils.strlen(cardno);
        if (len >= 13 && len <= 19) {
            ByteUtils.memset(pan, '0', 16);
            ByteUtils.memcpy(pan, 4, cardno, len - 13, 12);
            pan[16] = 0;
            return 0;
        } else {
            return -1;
        }
    }

    public static int PEDGetPwd_Api(int wkindex, int min, int max, byte[] cardNo, byte[] pin, int line, int mode) {
        int len = 8;
        int ret = false;
        byte[] Pan = new byte[25];
        byte[] Passwd = new byte[20];
        byte[] Pin_Block = new byte[16];
        ByteUtils.memset(Pin_Block, 255, Pin_Block.length);
        ByteUtils.memset(Pan, 0, Pan.length);
        LcdApi.ScrCls_Api();
        if (cardNo != null && pin != null) {
            if (wkindex > 2999) {
                return 5;
            } else if (mode != 3 && mode != 1 && mode != 2 && mode != 4 && mode != 5) {
                return 4;
            } else {
                if (ByteUtils.strlen(cardNo) != 0) {
                    ExtractPAN(cardNo, Pan);
                    byte[] temp = new byte[8];
                    MathsApi.AscToBcd_Api(temp, Pan, 16);
                    if (mode != 3 && mode != 1) {
                        if (mode == 2) {
                            len = 16;
                            ByteUtils.memcpy(Pan, temp);
                            ByteUtils.memset(Pan, 8, 0, 8);
                        } else if (mode == 4 || mode == 5) {
                            len = 16;
                            ByteUtils.memset(Pan, 0, 8);
                            ByteUtils.memcpy(Pan, 8, temp, 0, 8);
                            mode = 2;
                        }
                    } else {
                        len = 8;
                        ByteUtils.memcpy(Pan, temp);
                    }
                }

                if (max <= 12 && min <= max) {
                    int EditLen;
                    KeyListener keyListener;
                    do {
                        do {
                            LcdApi.ScrCls_Api();
                            keyListener = new KeyListener();
                            LcdApi.ShowPassWd(line, 0, keyListener, DispStr);
                            int ret = KeyApi.WaitAnyKey_Api(60);
                            if (ret != 125) {
                                DispStr = "";
                                if (ret != 123 && ret != 23) {
                                    return 3;
                                }

                                return 2;
                            }

                            EditLen = keyListener.getKeyValue().length();
                        } while(EditLen < min && EditLen != 0);
                    } while(EditLen > max);

                    DispStr = "";
                    if (keyListener.getKeyValue().length() == 0) {
                        ByteUtils.memset(pin, 0, 8);
                        return 0;
                    } else {
                        Passwd[0] = (byte)keyListener.getKeyValue().length();
                        ByteUtils.memcpy(Passwd, 1, keyListener.getKeyValue(), 0, keyListener.getKeyValue().length());
                        byte[] block = ByteUtils.subBytes(Pin_Block, 1);
                        MathsApi.AscToBcd_Api(block, ByteUtils.subBytes(Passwd, 1), Passwd[0]);
                        ByteUtils.memcpy(Pin_Block, 1, block, 0, block.length);
                        if (Passwd[0] % 2 != 0) {
                            Pin_Block[Passwd[0] / 2 + 1] = (byte)(Pin_Block[Passwd[0] / 2 + 1] | 15);
                        }

                        Pin_Block[0] = Passwd[0];

                        for(int i = 0; i < len; ++i) {
                            Pan[i] ^= Pin_Block[i];
                        }

                        if (PEDMac_Api(wkindex, mode, Pan, len, pin, 1) != 0) {
                            return 6;
                        } else {
                            return 0;
                        }
                    }
                } else {
                    return 237;
                }
            }
        } else {
            return 238;
        }
    }

    public static int PEDGetPwd_Api(String disMsg, byte[] panBlock, byte[] pinLimit, int keyIndex, int timeOut, int mode, IGetPinResultListenner listenner) {
        int Ret = 1;

        try {
            Ret = SdkApi.getInstance().getPedHandler().PEDGetPwd_Api(disMsg, panBlock, keyIndex, pinLimit, mode, timeOut, listenner);
        } catch (RemoteException var9) {
            RemoteException e = var9;
            e.printStackTrace();
        }

        return Ret;
    }

    public static int PEDGetExpress_Api(String disMsg, byte[] pinLimit, int timeOut, IGetPinResultListenner listenner) {
        int Ret = 1;

        try {
            Ret = SdkApi.getInstance().getPedHandler().PEDGetExpress_Api(disMsg, pinLimit, timeOut, listenner);
        } catch (RemoteException var6) {
            RemoteException e = var6;
            e.printStackTrace();
        }

        return Ret;
    }

    public static int PEDWriteMKey_Api(int mkindex, int mode, byte[] data) {
        int Ret = 1;

        try {
            Ret = SdkApi.getInstance().getPedHandler().PEDWriteMKey_Api(mkindex, mode, data);
        } catch (RemoteException var5) {
            RemoteException e = var5;
            e.printStackTrace();
        }

        return Ret;
    }

    public static int PEDWriteWKey_Api(int MkeyIndex, int WkeyIndex, int mode, byte[] data) {
        int Ret = 1;

        try {
            Ret = SdkApi.getInstance().getPedHandler().PEDWriteWKey_Api(MkeyIndex, WkeyIndex, mode, data);
        } catch (RemoteException var6) {
            RemoteException e = var6;
            e.printStackTrace();
        }

        return Ret;
    }

    public static int PEDMac_Api(int wkindex, int mode, byte[] data, int Len, byte[] Out, int flag) {
        int Ret = 1;

        try {
            Ret = SdkApi.getInstance().getPedHandler().PEDMac_Api(wkindex, mode, data, Len, Out, flag);
        } catch (RemoteException var8) {
            RemoteException e = var8;
            e.printStackTrace();
        }

        return Ret;
    }

    public static int PEDWriteIcBcKey_Api(byte[] inbuf, int AKeyIndes, int MasteKeyIndes, int MacKeyIndes, int PinKeyIndes, int Flag28, byte[] BitMap) {
        int Ret = 1;

        try {
            Ret = SdkApi.getInstance().getPedHandler().PEDWriteIcBcKey_Api(inbuf, AKeyIndes, MasteKeyIndes, MacKeyIndes, PinKeyIndes, Flag28, BitMap);
        } catch (RemoteException var9) {
            RemoteException e = var9;
            e.printStackTrace();
        }

        return Ret;
    }

    public static int PEDWriteIcBcKey_Api(byte[] Inbuf, int wkindex, int mkindex, int keyType, int akeyindex) {
        int Ret = 1;

        try {
            Ret = SdkApi.getInstance().getPedHandler().PEDWriteIcBcKeyWithType_Api(Inbuf, wkindex, mkindex, keyType, akeyindex);
        } catch (RemoteException var7) {
            RemoteException e = var7;
            e.printStackTrace();
        }

        return Ret;
    }

    public static int PEDDes_Api(int KeyIndex, int Mode, int MorWFlag, byte[] DataIn, int DataInLen, byte[] DataOut) {
        int Ret = 1;

        try {
            Ret = SdkApi.getInstance().getPedHandler().PEDDes_Api(KeyIndex, Mode, MorWFlag, DataIn, DataInLen, DataOut);
        } catch (RemoteException var8) {
            RemoteException e = var8;
            e.printStackTrace();
        }

        return Ret;
    }

    public static int PEDSavePinPadSn_Api(byte[] Sn) {
        int Ret = 1;

        try {
            Ret = SdkApi.getInstance().getPedHandler().PEDSavePinPadSn_Api(Sn);
        } catch (RemoteException var3) {
            RemoteException e = var3;
            e.printStackTrace();
        }

        return Ret;
    }

    public static int PEDReadPinPadSn_Api(byte[] Sn) {
        int Ret = 1;
        if (Sn == null) {
            return 238;
        } else {
            try {
                Ret = SdkApi.getInstance().getPedHandler().PEDReadPinPadSn_Api(Sn);
            } catch (RemoteException var3) {
                RemoteException e = var3;
                e.printStackTrace();
            }

            return Ret;
        }
    }

    public static int PEDWriteKey_Api(int SKeyIndex, int DKeyIndex, byte[] DKey, int DKeyType, int mode, byte[] KVRData) {
        int Ret = 1;
        if (DKey == null) {
            return 238;
        } else {
            try {
                Ret = SdkApi.getInstance().getPedHandler().PEDWriteKey_Api(SKeyIndex, DKeyIndex, DKey, DKeyType, mode, KVRData);
            } catch (RemoteException var8) {
                RemoteException e = var8;
                e.printStackTrace();
            }

            return Ret;
        }
    }

    public static int PEDDesCBC_Api(int KeyIndex, int Mode, int MorWFlag, byte[] ivIn, int ivLen, byte[] DataIn, int DataInLen, byte[] DataOut) {
        int Ret = 1;

        try {
            Ret = SdkApi.getInstance().getPedHandler().PEDDesCBC_Api(KeyIndex, Mode, MorWFlag, ivIn, ivLen, DataIn, DataInLen, DataOut);
        } catch (RemoteException var10) {
            RemoteException e = var10;
            e.printStackTrace();
        }

        return Ret;
    }

    public static int PedSelectPlace_Api(String Place) {
        int Ret = 1;

        try {
            Ret = SdkApi.getInstance().getPedHandler().PedSelectPlace_Api(Place);
        } catch (RemoteException var3) {
            RemoteException e = var3;
            e.printStackTrace();
        }

        return Ret;
    }

    public static int PEDSnMacOnly_Api(byte[] data, int dataLen, byte[] out, int mode) {
        int Ret = 1;

        try {
            Ret = SdkApi.getInstance().getPedHandler().PEDSnMacOnly_Api(data, dataLen, out, mode);
        } catch (RemoteException var6) {
            RemoteException e = var6;
            e.printStackTrace();
        }

        return Ret;
    }

    public static int PEDWrite21Key_Api(int mode, byte[] data) {
        int Ret = 1;

        try {
            Ret = SdkApi.getInstance().getPedHandler().PEDWrite21Key_Api(mode, data);
        } catch (RemoteException var4) {
            RemoteException e = var4;
            e.printStackTrace();
        }

        return Ret;
    }

    public static int PEDSetHdSoft_Api(int HdOrSoft) {
        int Ret = 1;

        try {
            Ret = SdkApi.getInstance().getPedHandler().PEDSetHdSoft_Api(HdOrSoft);
        } catch (RemoteException var3) {
            RemoteException e = var3;
            e.printStackTrace();
        }

        return Ret;
    }

    public static int getgHdOrSoft() {
        int Ret = 0;

        try {
            Ret = SdkApi.getInstance().getPedHandler().PEDGetHdSoft_Api();
        } catch (RemoteException var2) {
            RemoteException e = var2;
            e.printStackTrace();
        }

        return Ret;
    }

    /** @deprecated */
    public static int EDPPSetDesSmHdSoft_Api(int DesSMMode, int HdOrSoft) {
        int Ret = 1;
        return Ret;
    }

    public static void PEDDisp_Api(int nLineIndex, byte[] strText, int nLength, int nFlagSound) {
        DispStr = CommonConvert.Utf2Gbk(strText);
    }

    public static void PEDDisp_Api(String strText) {
        DispStr = strText;
    }

    public static native int WirteMkeyFY_Api(byte[] var0, int var1, byte[] var2, int var3, byte[] var4, int var5, int var6);

    /** @deprecated */
    public static int getFyTransKey_Api(byte[] out) {
        return 0;
    }

    /** @deprecated */
    public static void PEDHaveCallBack_Api() {
    }

    /** @deprecated */
    public static int PEDGetPwd_Api(int wkindex, int min, int max, byte[] cardNo, byte[] pin, int line, int mode, PedListener pedListener) {
        g_pedListener = pedListener;
        PEDHaveCallBack_Api();
        return 0;
    }

    private static void pedCallBack(int i, int j) {
        byte[] data = new byte[]{(byte)i, (byte)j};
        g_pedListener.processCallback(data);
    }

    /** @deprecated */
    public static void SetMkeyIndex_Api(int MkeyIndex) {
    }

    public static void PEDGetPwd_Api(final int wkindex, final byte[] pinLimit, String CardNo, final int mode, IKeyBoard board, final OnPedKeyListener pedKeyListener) {
        int ret = false;
        board.setKeyLen(ByteUtils.getMax(pinLimit));
        ByteUtils.memset(Pan, 0, Pan.length);
        if (CardNo == null) {
            pedKeyListener.onError(238);
        } else if (wkindex > 2999) {
            pedKeyListener.onError(4);
        } else if (mode != 3 && mode != 1 && mode != 2 && mode != 4 && mode != 5) {
            pedKeyListener.onError(4);
        } else {
            if (CardNo.length() != 0) {
                byte[] cardNo = new byte[CardNo.length()];
                ByteUtils.memcpy(cardNo, CardNo);
                ExtractPAN(cardNo, Pan);
                byte[] temp = new byte[8];
                MathsApi.AscToBcd_Api(temp, Pan, 16);
                boolean len;
                if (mode != 3 && mode != 1) {
                    if (mode == 2) {
                        len = true;
                        ByteUtils.memcpy(Pan, temp);
                        ByteUtils.memset(Pan, 8, 0, 8);
                    } else if (mode == 4 || mode == 5) {
                        len = true;
                        ByteUtils.memset(Pan, 0, 8);
                        ByteUtils.memcpy(Pan, 8, temp, 0, 8);
                    }
                } else {
                    len = true;
                    ByteUtils.memcpy(Pan, temp);
                }
            }

            try {
                board.setAmount(DispStr);
                board.setOnKeyBoardListener(new OnKeyBoardClickListener() {
                    public void onKeyBoardClick(View view, String key) {
                    }

                    public void onEnter(String keyValue) {
                        PedApi.DispStr = "";
                        int pinlen = 8;
                        int curmode = mode;
                        if (mode == 2) {
                            pinlen = 16;
                        } else if (mode == 4) {
                            pinlen = 16;
                            curmode = 2;
                        } else if (mode == 5) {
                            pinlen = 16;
                        }

                        byte[] Passwd = new byte[20];
                        byte[] Pin_Block = new byte[17];
                        byte[] pin = new byte[pinlen];
                        ByteUtils.memset(Pin_Block, 255, Pin_Block.length);
                        int EditLen = keyValue.length();
                        boolean checkFlag = false;

                        for(int i = 0; i < pinLimit.length; ++i) {
                            if (EditLen == pinLimit[i]) {
                                checkFlag = true;
                                break;
                            }
                        }

                        if (checkFlag) {
                            if (keyValue.length() == 0) {
                                ByteUtils.memset(pin, 0, pinlen);
                                pedKeyListener.onSuccess(keyValue);
                            } else {
                                Passwd[0] = (byte)keyValue.length();
                                ByteUtils.memcpy(Passwd, 1, keyValue, 0, keyValue.length());
                                byte[] block = ByteUtils.subBytes(Pin_Block, 1);
                                MathsApi.AscToBcd_Api(block, ByteUtils.subBytes(Passwd, 1), Passwd[0]);
                                ByteUtils.memcpy(Pin_Block, 1, block, 0, block.length);
                                if (Passwd[0] % 2 != 0) {
                                    Pin_Block[Passwd[0] / 2 + 1] = (byte)(Pin_Block[Passwd[0] / 2 + 1] | 15);
                                }

                                Pin_Block[0] = Passwd[0];

                                for(int ix = 0; ix < pinlen; ++ix) {
                                    byte[] var10000 = PedApi.Pan;
                                    var10000[ix] ^= Pin_Block[ix];
                                }

                                if (PedApi.PEDMac_Api(wkindex, curmode, PedApi.Pan, pinlen, pin, 1) != 0) {
                                    pedKeyListener.onError(6);
                                } else {
                                    pedKeyListener.onSuccess(CommonConvert.bytes2HexString(pin));
                                }
                            }
                        }
                    }

                    public void onCancel() {
                        PedApi.DispStr = "";
                        pedKeyListener.onCancel();
                    }

                    public void onKeyClick(int keyLen) {
                        if (keyLen <= ByteUtils.getMax(pinLimit)) {
                            pedKeyListener.onKeyClick(keyLen);
                        }

                    }
                });
            } catch (Exception var11) {
                DispStr = "";
                pedKeyListener.onError(7);
            }

        }
    }

    public static void PedSubmit(IKeyBoard board) {
        board.enter();
    }

    /** @deprecated */
    public static boolean isKeyExist(int keyType, int keyIndex) {
        return isKeyExist_Api(keyType, keyIndex);
    }

    public static boolean isKeyExist_Api(int keyType, int keyIndex) {
        boolean Ret = false;

        try {
            Ret = SdkApi.getInstance().getPedHandler().isKeyExist_Api(keyType, keyIndex);
        } catch (RemoteException var4) {
            RemoteException e = var4;
            e.printStackTrace();
        }

        return Ret;
    }

    /** @deprecated */
    public static boolean PedErase() {
        return PedErase_Api();
    }

    public static boolean PedErase_Api() {
        boolean Ret = false;

        try {
            Ret = SdkApi.getInstance().getPedHandler().PedEraseAll_Api();
        } catch (RemoteException var2) {
            RemoteException e = var2;
            e.printStackTrace();
        }

        return Ret;
    }

    /** @deprecated */
    public static boolean PedErase(int KeyType, int index) {
        return PedErase_Api(KeyType, index);
    }

    public static boolean PedErase_Api(int KeyType, int index) {
        boolean Ret = false;

        try {
            Ret = SdkApi.getInstance().getPedHandler().PedErase_Api(KeyType, index);
        } catch (RemoteException var4) {
            RemoteException e = var4;
            e.printStackTrace();
        }

        return Ret;
    }

    public static String PEDGetLastError_Api() {
        String lasterror = null;

        try {
            lasterror = SdkApi.getInstance().getPedHandler().PEDGetLastError_Api();
        } catch (RemoteException var2) {
            RemoteException e = var2;
            e.printStackTrace();
        }

        return lasterror;
    }

    public static int PEDGetPwdzh_Api(int wkindex, String CardNo, int mode, byte[] pincode, int pincodelen, byte[] pinOut) {
        int ret = false;
        if (wkindex > 2999) {
            return 1;
        } else if (mode != 3 && mode != 1 && mode != 2 && mode != 4 && mode != 5) {
            return 2;
        } else {
            ByteUtils.memset(Pan, 0, Pan.length);
            if (CardNo.length() != 0) {
                byte[] cardNo = new byte[CardNo.length()];
                ByteUtils.memcpy(cardNo, CardNo);
                ExtractPAN(cardNo, Pan);
                byte[] temp = new byte[8];
                MathsApi.AscToBcd_Api(temp, Pan, 16);
                boolean len;
                if (mode != 3 && mode != 1) {
                    if (mode == 2) {
                        len = true;
                        ByteUtils.memcpy(Pan, temp);
                        ByteUtils.memset(Pan, 8, 0, 8);
                    } else if (mode == 4 || mode == 5) {
                        len = true;
                        ByteUtils.memset(Pan, 0, 8);
                        ByteUtils.memcpy(Pan, 8, temp, 0, 8);
                    }
                } else {
                    len = true;
                    ByteUtils.memcpy(Pan, temp);
                }
            }

            int pinlen = 8;
            int curmode = mode;
            if (mode == 2) {
                pinlen = 16;
            } else if (mode == 4) {
                pinlen = 16;
                curmode = 2;
            } else if (mode == 5) {
                pinlen = 16;
            }

            byte[] Passwd = new byte[20];
            byte[] Pin_Block = new byte[17];
            ByteUtils.memset(Pin_Block, 255, Pin_Block.length);
            if (pincodelen == 0) {
                ByteUtils.memset(pinOut, 0, pinlen);
                return 0;
            } else {
                Passwd[0] = (byte)pincodelen;
                ByteUtils.memcpy(Passwd, 1, pincode, 0, pincodelen);
                byte[] block = ByteUtils.subBytes(Pin_Block, 1);
                MathsApi.AscToBcd_Api(block, ByteUtils.subBytes(Passwd, 1), Passwd[0]);
                ByteUtils.memcpy(Pin_Block, 1, block, 0, block.length);
                if (Passwd[0] % 2 != 0) {
                    Pin_Block[Passwd[0] / 2 + 1] = (byte)(Pin_Block[Passwd[0] / 2 + 1] | 15);
                }

                Pin_Block[0] = Passwd[0];

                for(int i = 0; i < pinlen; ++i) {
                    byte[] var10000 = Pan;
                    var10000[i] ^= Pin_Block[i];
                }

                return PEDMac_Api(wkindex, curmode, Pan, pinlen, pinOut, 1) == 0 ? 0 : 5;
            }
        }
    }

    public static int PEDGetEMVOfflinePin_Api(String disMsg, int min, int max, int timeOut) {
        int Ret = true;
        if (min >= 0 && max >= 0) {
            if (max > 12) {
                max = 12;
            }

            if (min > max) {
                min = max;
            }

            int limitLen = max - min + 2;
            byte[] pinLimit = new byte[limitLen];

            for(int i = 0; i < limitLen - 1; ++i) {
                pinLimit[i] = (byte)(min + i);
            }

            pinLimit[limitLen - 1] = 0;
            inputOfflineResult = -1;
            int Ret = PEDGetExpress_Api(disMsg, pinLimit, timeOut, new IGetPinResultListenner.Stub() {
                public void onTimerOut() throws RemoteException {
                    PedApi.inputOfflineResult = 3;
                }

                public void onError(int errcode, String msg) throws RemoteException {
                    if (errcode != 10) {
                        PedApi.inputOfflineResult = 4;
                    }

                }

                public void onEnter(byte[] pinOut) throws RemoteException {
                    if (pinOut == null || pinOut.length <= 0) {
                        pinOut = new byte[]{0};
                    }

                    EmvCallBackImpl.setOfflinePin(pinOut);
                    PedApi.inputOfflineResult = 0;
                }

                public void onClick(int inputLen) throws RemoteException {
                }

                public void onCancle() throws RemoteException {
                    PedApi.inputOfflineResult = 5;
                }
            });
            if (Ret != 0) {
                return 2;
            } else {
                do {
                    SystemApi.Delay_Api(100);
                } while(inputOfflineResult == -1);

                return inputOfflineResult;
            }
        } else {
            return 1;
        }
    }

    public static int PEDGetEMVOfflinePin_Api(String disMsg, byte[] pinLimit, int timeOut) {
        int Ret = true;
        inputOfflineResult = -1;
        int Ret = PEDGetExpress_Api(disMsg, pinLimit, timeOut, new IGetPinResultListenner.Stub() {
            public void onTimerOut() throws RemoteException {
                PedApi.inputOfflineResult = 3;
            }

            public void onError(int errcode, String msg) throws RemoteException {
                if (errcode != 10) {
                    PedApi.inputOfflineResult = 4;
                }

            }

            public void onEnter(byte[] pinOut) throws RemoteException {
                PedApi.inputOfflineResult = 0;
                if (pinOut == null || pinOut.length <= 0) {
                    PedApi.inputOfflineResult = 10;
                    pinOut = new byte[]{0};
                }

                EmvCallBackImpl.setOfflinePin(pinOut);
            }

            public void onClick(int inputLen) throws RemoteException {
            }

            public void onCancle() throws RemoteException {
                PedApi.inputOfflineResult = 5;
            }
        });
        if (Ret != 0) {
            return 2;
        } else {
            do {
                SystemApi.Delay_Api(100);
            } while(inputOfflineResult == -1);

            return inputOfflineResult;
        }
    }

    public static void PEDSetPinBoardStyle_Api(int PinBoardType) {
        try {
            SdkApi.getInstance().getPedHandler().setPinBoardStyle(PinBoardType);
        } catch (RemoteException var2) {
            RemoteException e = var2;
            e.printStackTrace();
        }

    }

    public static void PEDSetDispAmt_Api(String disAmt) {
        try {
            SdkApi.getInstance().getPedHandler().setDispAmt(disAmt);
        } catch (RemoteException var2) {
            RemoteException e = var2;
            e.printStackTrace();
        }

    }

    public static int PEDGetDukptPin_Api(String disMsg, byte[] dataIn, int keyIndex, byte[] pinLimit, int mode, int timeOut, IGetDukptPinListener listenner) {
        int ret = 0;
        if (dataIn == null) {
            return 238;
        } else {
            try {
                ret = SdkApi.getInstance().getPedHandler().PEDGetDukpt_Api(disMsg, dataIn, keyIndex, pinLimit, mode, timeOut, listenner);
            } catch (RemoteException var9) {
                RemoteException e = var9;
                e.printStackTrace();
            }

            return ret;
        }
    }

    public static int PedDukptWriteTIK_Api(byte GroupIdx, byte SrcKeyIdx, byte KeyLen, byte[] KeyValueIn, byte[] KsnIn, byte iCheckMode, byte[] aucCheckBuf) {
        int ret = 0;
        if (aucCheckBuf == null) {
            return 7;
        } else {
            try {
                ret = SdkApi.getInstance().getPedHandler().PedDukptWriteTIK_Api(GroupIdx, SrcKeyIdx, KeyLen, KeyValueIn, KsnIn, iCheckMode, aucCheckBuf);
            } catch (RemoteException var9) {
                RemoteException e = var9;
                e.printStackTrace();
            }

            return ret;
        }
    }

    public static int PedCalcDESDukpt_Api(byte GroupIdx, byte KeyVarType, byte[] KpucIV, byte[] DataIn, byte Mode, byte[] DataOut, byte[] KsnOut) {
        int ret = 0;
        if (DataIn == null) {
            return 6;
        } else {
            try {
                ret = SdkApi.getInstance().getPedHandler().PedCalcDESDukpt_Api(GroupIdx, KeyVarType, KpucIV, DataIn, Mode, DataOut, KsnOut);
            } catch (RemoteException var9) {
                RemoteException e = var9;
                e.printStackTrace();
            }

            return ret;
        }
    }

    public static int PedGetDukptKSN_Api(byte GroupIdx, byte[] KsnOut) {
        int ret = 0;
        if (KsnOut == null) {
            return 7;
        } else {
            try {
                ret = SdkApi.getInstance().getPedHandler().PedGetDukptKSN_Api(GroupIdx, KsnOut);
            } catch (RemoteException var4) {
                RemoteException e = var4;
                e.printStackTrace();
            }

            return ret;
        }
    }

    public static int PedDukptIncreaseKsn_Api(byte GroupIdx) {
        int ret = 0;

        try {
            ret = SdkApi.getInstance().getPedHandler().PedDukptIncreaseKsn_Api(GroupIdx);
        } catch (RemoteException var3) {
            RemoteException e = var3;
            e.printStackTrace();
        }

        return ret;
    }

    public static int PedGetMacDukpt_Api(byte GroupIdx, byte Increase, byte[] DataIn, int DataInLen, byte[] MacOut, byte[] KsnOut, byte Mode) {
        int ret = 0;

        try {
            ret = SdkApi.getInstance().getPedHandler().PedGetMacDukpt_Api(GroupIdx, Increase, DataIn, DataInLen, MacOut, KsnOut, Mode);
        } catch (RemoteException var9) {
            RemoteException e = var9;
            e.printStackTrace();
        }

        return ret;
    }

    public static int getPinDukptEx_Api(byte GroupIdx, byte mode, String pin, String data, byte[] pinBlockOut, byte[] ksnOut) {
        int ret = 0;

        try {
            ret = SdkApi.getInstance().getPedHandler().getPinDukptEx_Api(GroupIdx, mode, pin, data, pinBlockOut, ksnOut);
        } catch (RemoteException var8) {
            RemoteException e = var8;
            e.printStackTrace();
        }

        return ret;
    }

    /** @deprecated */
    public static void setCardNo(String cardNo) {
        setCardNo_Api(cardNo);
    }

    public static void setCardNo_Api(String cardNo) {
        try {
            SdkApi.getInstance().getPedHandler().setCardNo(cardNo);
        } catch (RemoteException var2) {
            RemoteException e = var2;
            e.printStackTrace();
        }

    }

    /** @deprecated */
    @Deprecated
    public static int writeRSAKey_Api(byte RSAKeyIndex, byte[] pstRsakeyIn) {
        int ret = 0;

        try {
            ret = SdkApi.getInstance().getPedHandler().writeRSAKey_Api(RSAKeyIndex, pstRsakeyIn);
        } catch (RemoteException var4) {
            RemoteException e = var4;
            e.printStackTrace();
        }

        return ret;
    }

    /** @deprecated */
    @Deprecated
    public static int calcRSA_Api(byte RSAKeyIndex, byte[] pucDataIn, byte[] pucDataOut, byte[] pucKeyInfoOut) {
        int ret = 0;

        try {
            ret = SdkApi.getInstance().getPedHandler().calcRSA_Api(RSAKeyIndex, pucDataIn, pucDataOut, pucKeyInfoOut);
        } catch (RemoteException var6) {
            RemoteException e = var6;
            e.printStackTrace();
        }

        return ret;
    }

    public static int writeRSAKeyEx_Api(int RSAKeyIndex, int iModulusLen, byte[] aucModulus, int iExponentLen, byte[] aucExponent, byte[] aucKeyInfo) {
        int ret = 0;
        if (aucKeyInfo == null) {
            return 238;
        } else {
            try {
                ret = SdkApi.getInstance().getPedHandler().writeRSAKeyEx_Api(RSAKeyIndex, iModulusLen, aucModulus, iExponentLen, aucExponent, aucKeyInfo);
            } catch (RemoteException var8) {
                RemoteException e = var8;
                e.printStackTrace();
            }

            return ret;
        }
    }

    public static int calcRSAEx_Api(int RSAKeyIndex, int pucDataInLen, byte[] pucDataIn, byte[] pucDataOut, byte[] pucKeyInfoOut) {
        int ret = 0;

        try {
            ret = SdkApi.getInstance().getPedHandler().calcRSAEx_Api(RSAKeyIndex, pucDataInLen, pucDataIn, pucDataOut, pucKeyInfoOut);
        } catch (RemoteException var7) {
            RemoteException e = var7;
            e.printStackTrace();
        }

        return ret;
    }

    /** @deprecated */
    public static void setTitleBackGroundColor(String titleBackGroundColor) {
        try {
            SdkApi.getInstance().getPedHandler().setTitleBackGroundColor(titleBackGroundColor);
        } catch (RemoteException var2) {
            RemoteException e = var2;
            e.printStackTrace();
        }

    }

    public static void setAmountSize(float amountSize) {
        try {
            SdkApi.getInstance().getPedHandler().setAmountSize(amountSize);
        } catch (RemoteException var2) {
            RemoteException e = var2;
            e.printStackTrace();
        }

    }

    public static void setAmountColor(String amountColor) {
        try {
            SdkApi.getInstance().getPedHandler().setAmountColor(amountColor);
        } catch (RemoteException var2) {
            RemoteException e = var2;
            e.printStackTrace();
        }

    }

    public static void setAmountFont(String amountFont) {
        try {
            SdkApi.getInstance().getPedHandler().setAmountFont(amountFont);
        } catch (RemoteException var2) {
            RemoteException e = var2;
            e.printStackTrace();
        }

    }

    public static void setTextSize(float textSize) {
        try {
            SdkApi.getInstance().getPedHandler().setTextSize(textSize);
        } catch (RemoteException var2) {
            RemoteException e = var2;
            e.printStackTrace();
        }

    }

    public static void setTextColor(String textColor) {
        try {
            SdkApi.getInstance().getPedHandler().setTextColor(textColor);
        } catch (RemoteException var2) {
            RemoteException e = var2;
            e.printStackTrace();
        }

    }

    public static void setTextFont(String textFont) {
        try {
            SdkApi.getInstance().getPedHandler().setTextFont(textFont);
        } catch (RemoteException var2) {
            RemoteException e = var2;
            e.printStackTrace();
        }

    }

    public static void setNumSize(float numSize) {
        try {
            SdkApi.getInstance().getPedHandler().setNumSize(numSize);
        } catch (RemoteException var2) {
            RemoteException e = var2;
            e.printStackTrace();
        }

    }

    public static void setNumColor(String numColor) {
        try {
            SdkApi.getInstance().getPedHandler().setNumColor(numColor);
        } catch (RemoteException var2) {
            RemoteException e = var2;
            e.printStackTrace();
        }

    }

    public static void setNumFont(String numFont) {
        try {
            SdkApi.getInstance().getPedHandler().setNumFont(numFont);
        } catch (RemoteException var2) {
            RemoteException e = var2;
            e.printStackTrace();
        }

    }

    public static void setBottomTextSize(float bottomTextSize) {
        try {
            SdkApi.getInstance().getPedHandler().setBottomTextSize(bottomTextSize);
        } catch (RemoteException var2) {
            RemoteException e = var2;
            e.printStackTrace();
        }

    }

    public static void setBottomTextColor(String bottomTextColor) {
        try {
            SdkApi.getInstance().getPedHandler().setBottomTextColor(bottomTextColor);
        } catch (RemoteException var2) {
            RemoteException e = var2;
            e.printStackTrace();
        }

    }

    public static void setBottomFont(String bottomFont) {
        try {
            SdkApi.getInstance().getPedHandler().setBottomFont(bottomFont);
        } catch (RemoteException var2) {
            RemoteException e = var2;
            e.printStackTrace();
        }

    }

    public static void setPinBoardFixed(boolean fixed) {
        try {
            SdkApi.getInstance().getPedHandler().setPinBoardFixed(fixed);
        } catch (RemoteException var2) {
            RemoteException e = var2;
            e.printStackTrace();
        }

    }

    public static void setBottomBtnText(String[] array) {
        try {
            SdkApi.getInstance().getPedHandler().setBottomBtnText(array);
        } catch (Exception var2) {
            Exception e = var2;
            e.printStackTrace();
        }

    }

    public static void setStatusbarHide(boolean isHide) {
        try {
            SdkApi.getInstance().getPedHandler().setStatusbarHide(isHide);
        } catch (Exception var2) {
            Exception e = var2;
            e.printStackTrace();
        }

    }

    public static void setStatusbarColor(String statusbarColor) {
        try {
            SdkApi.getInstance().getPedHandler().setStatusbarColor(statusbarColor);
        } catch (Exception var2) {
            Exception e = var2;
            e.printStackTrace();
        }

    }

    public static void setPinBoardMsg(Bundle bundle) {
        try {
            SdkApi.getInstance().getPedHandler().setPinBoardMsg(bundle);
        } catch (Exception var2) {
            Exception e = var2;
            e.printStackTrace();
        }

    }

    public static void setPinBoardSetting(Bundle bundle) {
        try {
            SdkApi.getInstance().getPedHandler().setPinBoardSetting(bundle);
        } catch (Exception var2) {
            Exception e = var2;
            e.printStackTrace();
        }

    }

    public interface OnPedKeyListener {
        void onSuccess(String var1);

        void onError(int var1);

        void onCancel();

        void onKeyClick(int var1);
    }
}
