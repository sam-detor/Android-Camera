package com.example.android_camera;

import com.example.stm32usbserial.CommanderPacket;
import com.example.stm32usbserial.CrtpPacket;
import com.example.stm32usbserial.PodUsbSerialService;

import java.util.List;

public class Driver {
    private double P=0.5;
    private double I=0;
    private double D=0;
    private double sizeSetpoint=245760;
    private double centerSetpoint = 340;
    private double frames_empty = 0;
    private MiniPID forwardPID = new MiniPID(P,I,D);
    private MiniPID sidePID = new MiniPID(P,I,D);

    Driver(){
        sidePID.setSetpoint(centerSetpoint);
        forwardPID.setSetpoint(sizeSetpoint);
    }

    public void drive(PodUsbSerialService mPodUsbSerialService,List<BoxWithText> detectedObjects){
        if(mPodUsbSerialService == null){
            return;
        }

        if(detectedObjects.size() == 0){
            if (frames_empty >= 3) {
                driveCar(mPodUsbSerialService,0,0);
            }
            frames_empty++;
            return;
        }
        double center = calculateCenter(detectedObjects);
        double size = calculateSize(detectedObjects);

    }

    private void driveCar(PodUsbSerialService mPodUsbSerialService, float forward, float side) {
         CommanderPacket cp = new CommanderPacket(side, forward, 0F, (short) 14000);
//                val cp: CommanderHoverPacket = CommanderHoverPacket(0.1F, 0F, 0F, 0.23F)
        mPodUsbSerialService.usbSendData(cp.toByteArray());
    }

    private double calculateCenter(List<BoxWithText> detectedObjects) {
        double center = 0;
        for (int i = 0; i < detectedObjects.size(); i++) {
            double indiv_center = 0;
            if (detectedObjects.get(i).getBox().left > detectedObjects.get(i).getBox().right) {
                indiv_center = (detectedObjects.get(i).getBox().left - detectedObjects.get(i).getBox().right) / 2 + detectedObjects.get(i).getBox().right;
            } else {
                indiv_center = (detectedObjects.get(i).getBox().right - detectedObjects.get(i).getBox().left) / 2 + detectedObjects.get(i).getBox().left;
            }
            center += indiv_center;
        }
        center = center / detectedObjects.size();
        return center;
    }
    //TODO: VERY NOT DONE
    private double calculateSize(List<BoxWithText> detectedObjects) {
        double size = 0;
        for (int i = 0; i < detectedObjects.size(); i++) {

            size += detectedObjects.get(i).getBox().top;
        }
        size = size / detectedObjects.size();
        return size;
    }




}
