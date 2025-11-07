*** keep Private! ***

# Sample app usage instructions

![image](https://github.com/zebratechnologies/cxnt48-visionSDK-ocr/assets/11386676/ef86907e-59d1-4509-b090-16056fc83160)


- Get the app here https://github.com/zebratechnologies/cxnt48-visionSDK-ocr/releases/download/CV-OCR/cv-ocr-cxnt48-v1.6d.apk or newer
- To do OCR on the camera live preview input
    - Either take a single shot: frame and push 'TAKE PHOTO'
  
    ![image](https://github.com/zebratechnologies/cxnt48-visionSDK-ocr/assets/11386676/589b39b9-2b41-49ed-800e-94d97c9a2d8a)

    - or use the LOOP feature to shoot a picture every 3 seconds and see the result in the output window
  ![image](https://github.com/zebratechnologies/cxnt48-visionSDK-ocr/assets/11386676/8682f839-636b-49a7-b43a-4dd7c4656155)

- Optionally
    - Choose to SAVE picture to the local storage: pictures are save as `/storage/emulated/0/Android/data/com.ndzl.cv.ocr/cache/ocr_*.jpg` (80% JPEG quality)     

    - Send OCR results to the LOG server: visit [https://cxnt48.com/log?1234](https://cxnt48.com/log?1234) and scroll down to the page's bottom to see the latest acquisition    


- To do OCR on preloaded pictures, follow these steps
    - rename your pictures as BATCH_original_name_here.jpg  This is easy to do in Windows explorer.
    - load such renamed picture to the `/storage/emulated/0/Android/data/com.ndzl.cv.ocr/cache/` folder.
    - run the app
        - set the LOG switch so results are uploaded to the server
        - and press the BATCH PROCESS button
        - see results at  [https://cxnt48.com/log?1234](https://cxnt48.com/log?1234)
     
      ![image](https://github.com/zebratechnologies/cxnt48-visionSDK-ocr/assets/11386676/4b9c9938-0084-4deb-b2fe-e552ee1bf6f6)



## LOG Server results example

![image](https://github.com/zebratechnologies/cxnt48-visionSDK-ocr/assets/11386676/8a78c0cb-3c64-4f63-bb46-c9399bd5c7a9)

- The first line of any batch report tells about the device that originated it: timestamp, OEM, BSP, AppId, App Version Name, Device's Android ID and originating IP

    `2024-lug-05 12:46:17 CEST::Zebra TechnologiesTC5813-30-04.00-TG-U00-STD-ATH-04 com.ndzl.cv.ocr-1.6-batch processing,A_ID=41264e94c5aaba69|212.177.18.68`

- The remaining lines are one for each picture processed, either from the camera preview or in batch mode. The meta data are: timestamp, file name, OCR result, capture mode, time to decode in millsec., Android ID to match the first line, IP
 
    `2024-lug-05 12:46:18 CEST::(BATCH_CARPLATE_ITA (121).jpg) <GH 984FB O > BATCH  (147ms) A_ID=41264e94c5aaba69 |212.177.18.68`



# GENERAL LINKS TO SAMPLE CODE AND OFFICIAL DOC

https://github.com/zebratechnologies/zebra-vision-sdk 
 
https://confluence.zebra.com/pages/viewpage.action?spaceKey=CAV&title=Zebra+Vision+SDK+Documentation 
 
https://cto-services.cortexica.com/tools/registry 



# PROJECT SETUP

[Thursday 22:38] De Zolt, Nicola
Maven download, getting error 401
Support Requests - Blocking issues What scopes should be granted to the generated token?
 
[Thursday 22:42] Kurella, Venu
For me Android studio specifically mentioned the required scopes. I think you are missing read packages. eg. this is mine 
 
[Thursday 22:46] De Zolt, Nicola
Thanks Venu, what about the username field? If my github address is https://github.com/NDZL
should I enter 
?
[Thursday 22:48] Kurella, Venu
thats right
[Thursday 22:51] De Zolt, Nicola
Now gettin error 403 instead of 401
Could not GET 'https://maven.pkg.github.com/zebratechnologies/zebra-vision-sdk/com/zebra/cvsdk/2.17.1/cvsdk-2.17.1.pom'. Received status code 403 from server: Forbidden

[Thursday 22:51] De Zolt, Nicola
Kurella, Venu can you check if NDZL is allowed to access the maven library thanks
[Thursday 22:52] Kurella, Venu
Have you authorised the token?
[Thursday 22:53] De Zolt, Nicola
I generated it like this
 
[Thursday 22:54] De Zolt, Nicola
and copy pasted in the build.gradle file
[Thursday 22:56] Kurella, Venu
authorization is here 
![image](https://github.com/NDZL/CTO-CV-OCR/assets/11386676/5e4d938e-16a9-4e00-90b7-24a67dd766c4)

 
[Thursday 22:56] De Zolt, Nicola
ok done thanks
Download https://maven.pkg.github.com/zebratechnologies/zebra-vision-sdk/com/zebra/cvsdk/2.17.1/cvsdk-2.17.1.aar, took 13 s 463 ms

[Thursday 22:57] Kurella, Venu
nice!
 like 1

 ![image](https://cxnt48.com/author?ghCVSDKocr) 
