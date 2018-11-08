#include <jni.h>
#include <opencv2/opencv.hpp>
#include <opencv2/photo.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/highgui.hpp>
#include <opencv2/core.hpp>
#include <opencv2/objdetect.hpp>


//Mat &preprocessing(Mat &mat);

using namespace cv;
using namespace std;
int blocksize;
int C = 5;
int gausian = 7;

//#include <string>
//
//extern "C" JNIEXPORT jstring JNICALL
//Java_com_tistory_webnautes_useopencvwithcmake_MainActivity_stringFromJNI(
//        JNIEnv *env,
//        jobject /* this */) {
//    std::string hello = "Hello from C++";
//    return env->NewStringUTF(hello.c_str());
//}

Mat preprocessing(Mat img) {
    Mat gray, th_img;

    float data[] = { 0, -1, 0, -1, 4, -1, 0, -1, 0 };
    cv::Mat kernel_2(3, 3, CV_32F, data);
    //cvtColor(img, img, CV_RGBA2GRAY);
    //cv::filter2D(img, gray, -1, kernel_2, cv::Point(-1, -1), 127.0, cv::BORDER_DEFAULT);
    cvtColor(img, gray, CV_RGBA2GRAY);

    //cvtColor(img, gray, CV_RGBA2GRAY);
    GaussianBlur(gray, gray, Size(gausian, gausian), 2, 2);

    //threshold(gray, th_img, 130, 255, THRESH_BINARY | THRESH_OTSU);
    adaptiveThreshold(gray, th_img, 255, ADAPTIVE_THRESH_MEAN_C, THRESH_BINARY, (blocksize*2)+1, 5);
    morphologyEx(th_img, th_img, MORPH_OPEN, Mat(), Point(-1, -1), -1);

    return th_img;
}

vector<RotatedRect> find_rects(Mat& img, vector<vector<Point>>& contours, vector<Moments>& M){

    findContours(img.clone(), contours, noArray(), RETR_LIST, CHAIN_APPROX_SIMPLE);
    //printf("www\n");
    Scalar blue(0, 0, 255);
    Scalar red(255, 0, 0);

    for (int k = 0; k < (contours).size(); k++) {
        drawContours(img, contours, k, blue, 1);
    }

    vector<RotatedRect> rects;
    for(int i = 0 ; i < (int)contours.size() ; i++){

        RotatedRect mr = minAreaRect(contours[i]);

        rects.push_back(mr);

    }
    return rects;
}

extern "C"
       JNIEXPORT void JNICALL
Java_com_tistory_webnautes_useopencvwithcmake_MainActivity_ConvertRGBtoGray(JNIEnv *env, jobject instance,
            jlong matAddrInput, jlong matAddrResult, jint a, jfloat x1, jfloat y1, jfloat x2, jfloat y2) {




    // 입력 RGBA 이미지를 GRAY 이미지로 변환
    Mat &matInput = *(Mat *)matAddrInput;
    Mat &matResult = *(Mat *)matAddrResult;

    int maxX = matInput.cols;
    int maxY = matInput.rows;
    int maxSize = maxX * maxY;

    vector<vector<Point>> contours;
    vector<Moments> M;

    blocksize = 15;
    Scalar red(255, 0, 0);
    Scalar blue(0, 0, 255);
    Point2f vertices[4];

    //float x_rate = (float)1366/1920;
    //float y_rate = (float)768/1080;
    int biggest_index = -1;
    float biggest_size = 0;
    int window_state = a;

    float tD_x = x1;                 //tD - touchdown 즉 안드로이드에서 input을 주었을 때  //원래 rate를 곱해줌
    float tD_y = y1;
    Point2f tD = {tD_x, tD_y};

    float cnt_x = maxX/2;
    float cnt_y = maxY/2;
    Point2f centerAim = {cnt_x, cnt_y};


    Mat bw_img = preprocessing(matInput);
    vector<RotatedRect> rects= find_rects(bw_img, contours, M);

    circle(bw_img, centerAim, 15, blue, 10, 8, 0);

    cvtColor(bw_img, bw_img, CV_GRAY2RGBA);


    for(int i = 0 ; i < rects.size() ;) {
        rects[i].points(vertices);

        if((unsigned)((vertices[0].x - vertices[1].x) * (vertices[1].y - vertices[2].y)) < 900){
            rects.erase(rects.begin() + i);
            contours.erase(contours.begin() + i);
        }
        else if ((unsigned)((vertices[0].x - vertices[1].x) * (vertices[1].y - vertices[2].y)) >= maxSize){
            rects.erase(rects.begin() + i);
            contours.erase(contours.begin() + i);
        }
        else{
            i++;
        }
    }       //900보다 rect가 크기가 작다면 현실적으로 그건 고려대상에서 제외한다.

    for(int i = 0 ; i < rects.size() ;i++) {
        M.push_back(moments(contours[i]));
    }

    int nearClickIdx = -1;
    float distance = 0;
    float shortestDis = -1;
    for(int i = 0; i < contours.size(); i++){
        Point2f center;
        center.x = M[i].m10/M[i].m00;
        center.y = M[i].m01/M[i].m00;

        distance = sqrt(pow(center.x - cnt_x, 2) + pow(center.y - cnt_y, 2));
        if (shortestDis < 0 || shortestDis > distance) {
            shortestDis = distance;
            nearClickIdx = i;
        }
    }


//    for(int i = 0 ; i < rects.size() ; i++) {
//        rects[i].points(vertices);
//
//        if((unsigned)((vertices[0].x - vertices[1].x) * (vertices[1].y - vertices[2].y)) > biggest_size){
//            biggest_index = i;
//            biggest_size = (unsigned)((vertices[0].x - vertices[1].x) * (vertices[1].y - vertices[2].y));
//        }
//    }       // 가장 큰 물체를 찾는 알고리즘


    for (int i = 0 ; i < rects.size(); i++){
        rects[nearClickIdx].points(vertices);
        for (int j = 0; j < 4; j++) {
            line(bw_img, vertices[j], vertices[(j + 1) % 4], red, 2);
            line(matInput, vertices[j], vertices[(j + 1) % 4], red, 2);
        }
    }

//    for (int k = 0; k < (contours).size(); k++) {
//        drawContours(bw_img, contours, k, red, 2);
//    } //contours 다 나타내는 건데 정신사납다.

//    for (int k = 0; k < (contours[nearClickIdx]).size() - 1; k++){
//        line(bw_img, contours[nearClickIdx][k], contours[nearClickIdx][k+1], blue, 3);
//        line(matInput, contours[nearClickIdx][k], contours[nearClickIdx][k+1], blue, 3);
//    }

    if(window_state == 1) {
        matResult = bw_img;
    }
    else if(window_state == 2) {
        matResult = matInput;
    }

}