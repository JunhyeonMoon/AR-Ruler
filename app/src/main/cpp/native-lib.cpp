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
int min_;
int max_;
int C = 5;
int gausian = 7;
int mode;
vector<vector<Point>> contours;

Mat ZeroCrossing(Mat &src, int th){
    int x, y;
    double a, b;
    Mat dst;

    Mat zeroCrossH(src.size(), CV_8UC3, Scalar::all(0));
    Mat_<float>_src(src);
    for(y = 1 ; y < src.rows-1 ; y++)
        for(x = 1 ; x < src.cols-1 ; x++){
            a = _src(y, x);
            b = _src(y, x+1);
            if(a == 0){
                a = _src(y, x-1);
            }
            if(a * b < 0){
                zeroCrossH.at<float>(y, x) = fabs(a) + fabs(b);
            }
            else{
                zeroCrossH.at<float>(y, x) = 0;
            }
        }

    Mat zeroCrossV(src.size(), CV_8UC3, Scalar::all(0));
    for(y = 1 ; y < src.rows-1 ; y++)
        for(x = 1 ; x < src.cols-1 ; x++){
            a = _src(y, x);
            b = _src(y+1, x);
            if(a == 0){
                a = _src(y-1, x);
            }
            if(a * b < 0){
                zeroCrossV.at<float>(y, x) = fabs(a) + fabs(b);
            }
            else{
                zeroCrossV.at<float>(y, x) = 0;
            }
        }

    Mat zeroCross(src.size(), CV_8UC3, Scalar::all(0));
    add(zeroCrossH, zeroCrossV, zeroCross);
    threshold(zeroCross, dst, th, 255, THRESH_BINARY);
    return dst;
}

Mat preprocessing(Mat img) {
    Mat gray, lap, dst, th_img;

    float data[] = { 0, -1, 0, -1, 4, -1, 0, -1, 0 };
    cv::Mat kernel_2(3, 3, CV_32F, data);
    //cvtColor(img, img, CV_RGBA2GRAY);
    //cv::filter2D(img, gray, -1, kernel_2, cv::Point(-1, -1), 127.0, cv::BORDER_DEFAULT);
    cvtColor(img, gray, CV_RGBA2GRAY);
    //cvtColor(gray, gray, CV_BGR2HSV);

    //cvtColor(img, gray, CV_RGBA2GRAY);
    GaussianBlur(gray, gray, Size(gausian, gausian), 2, 2);
    //Laplacian(th_img, lap, CV_8UC3, 15);
    //dst = ZeroCrossing(lap, 10);
    //Canny(gray, gray, min_, max_);

    //threshold(gray, th_img, 130, 255, THRESH_BINARY | THRESH_OTSU);
    adaptiveThreshold(gray, th_img, 255, ADAPTIVE_THRESH_MEAN_C, mode, (blocksize*2)+1, 5);
    morphologyEx(th_img, th_img, MORPH_OPEN, Mat(), Point(-1, -1), -1);

    return th_img;
    //return dst;
}

vector<RotatedRect> find_rects(Mat img){
    //vector<vector<Point>> contours;
    //외곽선 검출
    //Mat newimg = new Mat(img.size(), CvType.CV_8UC1);
    findContours(img.clone(), contours, RETR_LIST, CHAIN_APPROX_SIMPLE, Point(0, 0));
    //printf("www\n");
    vector<RotatedRect> rects;
    for(int i = 0 ; i < (int)contours.size() ; i++){
        if(contours[i].size() > 100) {

            RotatedRect mr = minAreaRect(contours[i]);

            rects.push_back(mr);
        }

    }
    return rects;
}

//#include <string>

//extern "C" JNIEXPORT jstring JNICALL
//Java_com_example_whgns_ex1_MainActivity_stringFromJNI(
//        JNIEnv *env,
//        jobject /* this */) {
//    std::string hello = "Hello from C++";
//    return env->NewStringUTF(hello.c_str());
//}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_example_paranocs_ar_1ruler_MainActivity_ConvertRGBtoGray(JNIEnv *env, jobject instance, jlong matAddrInput){

    // TODO
    // 입력 RGBA 이미지를 GRAY 이미지로 변환
    Mat &matInput = *(Mat *)matAddrInput;

    //blocksize = 15;
    blocksize = 7;
    //min_ = b;
    //max_ = c;
    Scalar red(255, 0, 0);
    Scalar blue(0, 0, 255);
    Scalar green(0, 255, 0);
    Scalar purple(255, 0, 255);
    Scalar orange(0, 165, 255);
    Point2f vertices[4];

    float s_x = 0;
    float s_y = 0;
    float e_x = 0;
    float e_y = 0;
    float x_rate = (float)1366/1920;
    float y_rate = (float)768/1080;
    int biggest_index = -1;
    float biggest_size = 0;
    int window_state = 1;

    //if(c == 1){
    //    mode = THRESH_BINARY_INV;
    //}
    //else if(c == 2){
    //    mode = THRESH_BINARY;
    //}
    mode = THRESH_BINARY_INV;

    //setMouseCallback("mouse", onmouse, 0);

    //float data[] = { 0, -1, 0, -1, 4, -1, 0, -1, 0 };
    //cv::Mat kernel_2(3, 3, CV_32F, data);

    //cv::filter2D(matInput, matResult, -1, kernel_2);

    //cv::filter2D(matInput, matResult, -1, kernel_2, cv::Point(-1, -1), 127.0, cv::BORDER_DEFAULT);

    //cvtColor(matInput, matResult, CV_RGBA2GRAY);


    //namedWindow("Block", WINDOW_AUTOSIZE);

    Mat bw_img = preprocessing(matInput);
    vector<RotatedRect> rects = find_rects(bw_img);
    cvtColor(bw_img, bw_img, CV_GRAY2RGBA);



    /*for(int i = 0 ; i < rects.size() ; i++){

        rects[i].points(vertices);

        if(norm(vertices[0] - vertices[1]) > 10 && norm(vertices[1] - vertices[2]) > 10) {
            for (int j = 0; j < 4; j++) {
                line(bw_img, vertices[j], vertices[(j + 1) % 4], red, 2);
            }
        }
    }*/

    //범위 내의 가장큰 사각형의 index 추출
    /*for(int i = 0 ; i < rects.size() ; i++) {

        rects[i].points(vertices);

        if (((vertices[0].x > s_x && vertices[0].x < e_x) &&
             (vertices[0].y > s_y && vertices[0].y < e_y)) &&
            ((vertices[1].x > s_x && vertices[1].x < e_x) &&
             (vertices[1].y > s_y && vertices[1].y < e_y)) &&
            ((vertices[2].x > s_x && vertices[2].x < e_x) &&
             (vertices[2].y > s_y && vertices[2].y < e_y)) &&
            ((vertices[3].x > s_x && vertices[3].x < e_x) &&
             (vertices[3].y > s_y && vertices[3].y < e_y))) {
            //for (int j = 0; j < 4; j++) {
            //    line(bw_img, vertices[j], vertices[(j + 1) % 4], red, 2);
            //}
            if((unsigned)((vertices[0].x - vertices[1].x) * (vertices[1].y - vertices[2].y)) > biggest_size){
                biggest_index = i;
                biggest_size = (unsigned)((vertices[0].x - vertices[1].x) * (vertices[1].y - vertices[2].y));
            }
        }
    }*/


    //두 화면에 가장큰 사각형 그리기
    /*if(biggest_index >= 0) {
        rects[biggest_index].points(vertices);
        for (int j = 0; j < 4; j++) {
            line(bw_img, vertices[j], vertices[(j + 1) % 4], red, 2);
            line(matInput, vertices[j], vertices[(j + 1) % 4], red, 2);
        }
    }

    line(bw_img, p1, p2, blue, 2);
    line(bw_img, p2, p3, blue, 2);
    line(bw_img, p3, p4, blue, 2);
    line(bw_img, p4, p1, blue, 2);
    line(matInput, p1, p2, blue, 2);
    line(matInput, p2, p3, blue, 2);
    line(matInput, p3, p4, blue, 2);
    line(matInput, p4, p1, blue, 2);
     */


    //line(bw_img, {0, 0}, {1366, 768}, blue, 2);
    //line(bw_img, p5, p6, blue, 2);



    /*for(int i = 0 ; i < rects.size() ; i++) {

        rects[i].points(vertices);
        for (int j = 0; j < 4; j++) {
            line(bw_img, vertices[j], vertices[(j + 1) % 4], red, 2);
        }
    }*/

    /*if((int)s_x > 1316 && (int)s_y > 718) {
       if(window_state == 0){
           window_state = 1;
       }
       else if(window_state ==1){
           window_state = 0;
       }
    }*/
    /*for(int i = 0, j = 0; j < contours.size(); j++) {
        if(contours[i].size() < 10) {
            contours[i].pop_back();
        }
        else{
            i++;
        }
    }*/

    vector<vector<Point2i>> hulls;
    vector<Point2i> hull;
    if(contours.size() > 0) {
        for (int i = 0; i < contours.size(); i++) {
            if (contours[i].size() > 200 && contours[i].size() < 5000) {
                drawContours(bw_img, contours, i, green, 2);

                convexHull(contours[i], hull);
                hulls.push_back(hull);

            }
        }
    }

    //drawContours(bw_img, contours, 100, red, 2);



    for(int i = 0 ; i < hulls.size(); i++) {
        drawContours(bw_img, hulls , i, blue, 2);
    }



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


    vector<vector<Point2i>> mains;
    for(int i = 0 ; i < centers.size(); i++){
        if((centers[i].x < matInput.cols - 200 && centers[i].x > 200) && (centers[i].y < matInput.rows - 100 && centers[i].y > 100) ){
            mains.push_back(hulls[i]);
        }
    }


    vector<Point2i> main_points;
    for(int i = 0 ; i < mains.size() ; i++){
        for(int j = 0 ; j < mains[i].size() ; j++){
            if((mains[i][j].x < matInput.cols - 200 && mains[i][j].x > 200) && (mains[i][j].y < matInput.rows - 100 && mains[i][j].y > 100) ) {
                main_points.push_back(mains[i][j]);
                circle(bw_img, mains[i][j], 10, purple, -1);
            }
            else{
                circle(bw_img, mains[i][j], 10, orange, -1);
            }
        }
    }

    /*vector<Point2i> sub_points;
    Point2i p7;
    for(int i = 0 ; i < hulls.size(); i++) {
        for (int j = 0; j < hulls[i].size(); j++) {

            p7.x = hulls[i][j].x;
            p7.y = hulls[i][j].y;
            sub_points.push_back(p7);
        }
    }*/

    //RotatedRect final_rr  = minAreaRect(result_hull);
    //Point2f rectPoints[4];
    //final_rr.points(rectPoints);
    //Rect final_r = boundingRect(result_hull);

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


    //RotatedRect final_rr  = minAreaRect(result_hulls[0]);
    //Point2f rectPoints[4];
    //final_rr.points(rectPoints);
    //Rect final_r = boundingRect(result_hulls[0]);

    //drawContours(bw_img, result_hulls, 0, red, 2);
    Point2f rectPoints[4];
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
            Rect final_r = boundingRect(result_hulls[0]);
            __android_log_print(ANDROID_LOG_ERROR, "asd", "6");
            rectangle(matInput, final_r, blue, 2);
            __android_log_print(ANDROID_LOG_ERROR, "asd", "opencv log88888!!");
            for(int i = 0 ; i < 4 ; i++) {
                line(matInput, rectPoints[i], rectPoints[(i + 1) % 4], green, 2);
                __android_log_print(ANDROID_LOG_ERROR, "asd", "opencv log!!99999");

            }
        }
        __android_log_print(ANDROID_LOG_ERROR, "asd", "opencv log10101010!!");
        jfloatArray result;
        result = env->NewFloatArray(8);
        jfloat array[8];
        array[0] = rectPoints[0].x;
        array[1] = rectPoints[0].y;
        array[2] = rectPoints[1].x;
        array[3] = rectPoints[1].y;
        array[4] = rectPoints[2].x;
        array[5] = rectPoints[2].y;
        array[6] = rectPoints[3].x;
        array[7] = rectPoints[3].y;

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


    //result_hulls.push_back(result_hull);
    /*for(int i = 0 ; i < hulls.size(); i++) {
        if(hulls[i].size() > 100){
            //drawContours(bw_img, contours, i, red, 2);

            convexHull(hulls[0], result_hull);
            result_hulls.push_back(result_hull);
            break;
        }
    }*/

    /*for(int i = 0 ; i < contours.size(); i++) {
        if(contours[i].size() > 100){
            drawContours(bw_img, contours, i, red, 2);

            convexHull(contours[i], hull);
            hulls.push_back(hull);

        }
    }*/




//    if(window_state == 1) {
//        matResult = bw_img;
//        //matResult = matInput;
//    }
//    else if(window_state == 2) {
//        matResult = matInput;
//    }
//    //createTrackbar("Block_size", "Block", &blocksize, 120, onchange);
//    //setTrackbarPos("block_size", "block", 50);
}



