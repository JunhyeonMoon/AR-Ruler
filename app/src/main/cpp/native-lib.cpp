#include <jni.h>
#include <opencv2/opencv.hpp>
#include <opencv2/photo.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/highgui.hpp>
#include <opencv2/core.hpp>
#include <opencv2/objdetect.hpp>
#include <android/log.h>

//Mat &preprocessing(Mat &mat);

using namespace cv;
using namespace std;
int blocksize;
int C = 5;
int gausian = 7;
int mode;
vector<vector<Point>> contours;

//Input Image로 부터 Contours를 쉽게 찾기 위해 실행하는 함수
Mat preprocessing(Mat img) {
    Mat gray, th_img;

    //이미지를 RGB 칼라에서 GRAY 칼라로 변경
    cvtColor(img, gray, CV_RGBA2GRAY);

    //가우시안블러를 사용해서 이미지를 전체적으로 흐리게 만들어줌
    GaussianBlur(gray, gray, Size(gausian, gausian), 2, 2);

    //adaptiveThreshold 함수를 사용해서 임계값과 옵션을 적용하여 전체 이미지를 흑,백으로 나눔
    adaptiveThreshold(gray, th_img, 255, ADAPTIVE_THRESH_MEAN_C, mode, (blocksize*2)+1, 5);

    //주변 잡음 제거
    morphologyEx(th_img, th_img, MORPH_OPEN, Mat(), Point(-1, -1), -1);

    return th_img;
}

//변경된 이미지를 사용해서 Contours와 그에 맞는 rectangles을 찾는 함수
vector<RotatedRect> find_rects(Mat img){

    //외곽선 검출
    findContours(img.clone(), contours, RETR_LIST, CHAIN_APPROX_SIMPLE, Point(0, 0));

    vector<RotatedRect> rects;
    for(int i = 0 ; i < (int)contours.size() ; i++){
        //경계선의 점갯수 제한 -> 너무 작은 경계선 배제
        if(contours[i].size() > 100) {

            RotatedRect mr = minAreaRect(contours[i]);

            rects.push_back(mr);
        }

    }
    return rects;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_example_paranocs_ar_1ruler_ArviewActivity_ConvertRGBtoGray(JNIEnv *env, jobject instance, jlong matAddrInput){

    // TODO

    Mat &matInput = *(Mat *)matAddrInput;

    __android_log_print(ANDROID_LOG_ERROR, "wwwww", "width = %d", matInput.cols);
    __android_log_print(ANDROID_LOG_ERROR, "wwwww", "height = %d", matInput.rows);

    //blocksize = 15;
    blocksize = 7;
    Scalar red(255, 0, 0);
    Scalar blue(0, 0, 255);
    Scalar green(0, 255, 0);
    Scalar purple(255, 0, 255);
    Scalar orange(0, 165, 255);
    Point2f vertices[4];

    int window_state = 1;
    mode = THRESH_BINARY_INV;

    Mat bw_img = preprocessing(matInput);
    vector<RotatedRect> rects = find_rects(bw_img);
    cvtColor(bw_img, bw_img, CV_GRAY2RGBA);


    //경계선 들에 점 갯수 제한을 걸고 만족하는 경계선들만 hulls vector에 넣는다.
    vector<vector<Point2i>> hulls;
    vector<Point2i> hull;
    if(contours.size() > 0) {
        for (int i = 0; i < contours.size(); i++) {
            if (contours[i].size() > 100 && contours[i].size() < 5000) {
                drawContours(bw_img, contours, i, green, 2);

                convexHull(contours[i], hull);
                hulls.push_back(hull);

            }
        }
    }


    for(int i = 0 ; i < hulls.size(); i++) {
        drawContours(bw_img, hulls , i, blue, 2);
    }



    //hulls 내의 경계선 들의 좌표중심을 계산
    int c_x = 0;
    int c_y = 0;
    vector<Point2i> centers;
    for(int i = 0 ; i < hulls.size(); i++){
        for(int j = 0 ; j < hulls[i].size(); j++){
            c_x += hulls[i][j].x;
            c_y += hulls[i][j].y;
        }
        c_x = (int)(c_x / hulls[i].size());
        c_y = (int)(c_y / hulls[i].size());
        Point2i p;
        p.x = c_x;
        p.y = c_y;
        centers.push_back(p);
        c_x = 0;
        c_y = 0;
    }

    //화면 위,아래,왼쪽,오른쪽에 경계를 만들어서 너무 외곽에 존재라는 경계선들을뺀 나머지 경계선들을
    //mains vector에 넣는다.
    vector<vector<Point2i>> mains;
    for(int i = 0 ; i < centers.size(); i++){
        if((centers[i].x < matInput.cols - 40 && centers[i].x > 40) && (centers[i].y < matInput.rows - 30 && centers[i].y > 30) ){
            mains.push_back(hulls[i]);
        }
    }


    //위의 기능에서 이어져서 만들어진 경계내에 존재하는 점들은 보라색으로, 그외의 점들은 오렌지 색으로
    //점을 찍는다. 그리고 만족하는 점들을 main_points vector에 넣는다.
    vector<Point2i> main_points;
    for(int i = 0 ; i < mains.size() ; i++){
        for(int j = 0 ; j < mains[i].size() ; j++){
            if((mains[i][j].x < matInput.cols - 40 && mains[i][j].x > 40) && (mains[i][j].y < matInput.rows - 30 && mains[i][j].y > 30) ) {
                main_points.push_back(mains[i][j]);
                circle(bw_img, mains[i][j], 10, purple, -1);
            }
            else{
                circle(bw_img, mains[i][j], 10, orange, -1);
            }
        }
    }

    //Convexhull을 사용해서 main_points에 존재하는 점들을 감싸는 다각형을 만들고 이를
    //result_hulls vector에 넣는다
    vector<Point2i> result_hull;
    vector<vector<Point2i>> result_hulls;
    if(main_points.size() > 3) {
        for (int i = 0; i < hulls.size(); i++) {
            if (hulls[i].size() > 1) {
                convexHull(main_points, result_hull);
                result_hulls.push_back(result_hull);
                break;
            }
        }
    }

    //drawContours(bw_img, result_hulls, 0, red, 2);

    //result_hulls에 있는 최종적인 다각형을 감싸는 가장 면적이 작은 사각형을 구하고
    //사각형의 각 꼭지점 좌표를 MainActivity로 return한다.
    //사각형을 찾지 못했을 경우에는 네점 모두 (-1, -1)을 return한다.
    Point2f rectPoints[4];
    Point2f rectPoints2[4];
    Rect final_r;
    int error_check = 0;
    int size = result_hulls.size();
    __android_log_print(ANDROID_LOG_ERROR, "asd", "size = %d", size);
    if(result_hulls.size() > 0) {
        for (int i = 0; i < result_hulls.size(); i++) {
            __android_log_print(ANDROID_LOG_ERROR, "asd", "1");
            drawContours(bw_img, result_hulls, i, red, 2);
            __android_log_print(ANDROID_LOG_ERROR, "asd", "2");
            drawContours(matInput, result_hulls, i, red, 2);
            __android_log_print(ANDROID_LOG_ERROR, "asd", "3");
            RotatedRect final_rr  = minAreaRect(result_hulls[0]);
            __android_log_print(ANDROID_LOG_ERROR, "asd", "4");
            final_rr.points(rectPoints);
            __android_log_print(ANDROID_LOG_ERROR, "asd", "5");
            final_r = boundingRect(result_hulls[0]);
            __android_log_print(ANDROID_LOG_ERROR, "asd", "6");
            rectangle(matInput, final_r, blue, 2);
            __android_log_print(ANDROID_LOG_ERROR, "asd", "opencv log88888!!");
            for(int i = 0 ; i < 4 ; i++) {
                line(matInput, rectPoints[i], rectPoints[(i + 1) % 4], green, 2);
                __android_log_print(ANDROID_LOG_ERROR, "asd", "opencv log!!99999");

            }
        }
        for(int i = 0; i < 4 ; i++){
            if((rectPoints[i].x > 640.f || rectPoints[i].x < 0.f) || (rectPoints[i].y > 480.f || rectPoints[i].y < 0.f)){
                error_check = 1;
            }
        }
        __android_log_print(ANDROID_LOG_ERROR, "asd", "opencv log10101010!!");
        jfloatArray result;
        result = env->NewFloatArray(8);
        jfloat array[8];
        if(!error_check) {
            array[0] = rectPoints[0].x;
            array[1] = rectPoints[0].y;
            array[2] = rectPoints[1].x;
            array[3] = rectPoints[1].y;
            array[4] = rectPoints[2].x;
            array[5] = rectPoints[2].y;
            array[6] = rectPoints[3].x;
            array[7] = rectPoints[3].y;
        }
        else{
            array[0] = final_r.tl().x;
            array[1] = final_r.tl().y;
            array[2] = final_r.br().x;
            array[3] = final_r.tl().y;
            array[4] = final_r.br().x;
            array[5] = final_r.br().y;
            array[6] = final_r.tl().x;
            array[7] = final_r.br().y;
        }

        env->SetFloatArrayRegion(result, 0, 8, array);

        return result;

    }else{
        jfloatArray result;
        result = env->NewFloatArray(8);
        jfloat array[8];
        array[0] = -1.f;
        array[1] = -1.f;
        array[2] = -1.f;
        array[3] = -1.f;
        array[4] = -1.f;
        array[5] = -1.f;
        array[6] = -1.f;
        array[7] = -1.f;

        env->SetFloatArrayRegion(result, 0, 8, array);
        return result;
    }
}



