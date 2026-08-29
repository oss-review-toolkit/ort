#include "lib/cookie-jar.h"
#include <glog/logging.h>

int main(int argc, char* argv[]) {
    google::InitGoogleLogging(argv[0]);

    int num_cookies = grab_cookies();
    LOG(INFO) << "Found " << num_cookies << " cookies";
}

