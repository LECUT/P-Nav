#include <jni.h>
#include "rtklib.h"

// 定义全局变量
// 定义全局变量
rtcm_t *RTCM;   /* RTCM3 解码器实例 */
rtcm_t *RTCM2;  /* RTCM2 解码器实例 */
__attribute__((unused)) int sat1 = 0;
int nn=0;
int n1=0;
int n2=0;
int n3=0;
int n4=0;
int count1=0;
int count2=0;
int count3=0;

/* ── 缓存的 JNI Field ID，避免每历元每卫星重复查找 ── */
static jfieldID f_obs_P    = NULL;
static jfieldID f_obs_L    = NULL;
static jfieldID f_obs_D    = NULL;
static jfieldID f_obs_SNR  = NULL;
static jfieldID f_obs_sat  = NULL;
static jfieldID f_obs_code = NULL;
static jfieldID f_time_time = NULL;
static jfieldID f_time_sec  = NULL;
static jclass   cachedObsClass  = NULL;
static jclass   cachedTimeClass = NULL;

/* ── 按需缓存 Field ID（首次调用时查找，后续复用） ── */
static void ensureFieldIds(JNIEnv *env, jobject obss, jobject obstime) {
    if (cachedObsClass != NULL) return;  /* 已缓存 */
    cachedObsClass  = (*env)->GetObjectClass(env, (*env)->GetObjectArrayElement(env, obss, 0));
    cachedTimeClass = (*env)->GetObjectClass(env, (*env)->GetObjectArrayElement(env, obstime, 0));
    f_obs_P    = (*env)->GetFieldID(env, cachedObsClass, "P",   "[D");
    f_obs_L    = (*env)->GetFieldID(env, cachedObsClass, "L",   "[D");
    f_obs_D    = (*env)->GetFieldID(env, cachedObsClass, "D",   "[F");
    f_obs_SNR  = (*env)->GetFieldID(env, cachedObsClass, "SNR", "[F");
    f_obs_sat  = (*env)->GetFieldID(env, cachedObsClass, "sat", "[I");
    f_obs_code = (*env)->GetFieldID(env, cachedObsClass, "code","[I");
    f_time_time = (*env)->GetFieldID(env, cachedTimeClass, "time", "D");
    f_time_sec  = (*env)->GetFieldID(env, cachedTimeClass, "sec",  "D");
}



JNIEXPORT jint JNICALL
Java_com_ecut_pnav_ui_Ntrip_NtripFragment_inputrtcm3(
        JNIEnv *env,jobject thiz, jobject rtcm,
        jbyte data, jobject obss, jobject obstime, jdoubleArray stapos) {
    // 将 jbyte 转换为 uint8_t
    uint8_t data_uint8 = (uint8_t)data;
    jdouble time = 0;
    jdouble sec = 0;
    jint satValue = 0;
    int result = input_rtcm3(RTCM, data_uint8);
    if (result != 0) {
        // 将 C 数组复制到 stapos（复用传入的 jdoubleArray，不分配新的）
        if (stapos != NULL) {
            (*env)->SetDoubleArrayRegion(env, stapos, 0, 3, RTCM->sta.pos);
        }
        // 缓存 Field ID（首次调用后不再查找）
        ensureFieldIds(env, obss, obstime);
        for (int i = 0; i < RTCM->obs.n; i++) {
            satValue = (jint) RTCM->obs.data[i].sat;
            time = RTCM->obs.data[i].time.time;
            sec = RTCM->obs.data[i].time.sec;

            jobject obsd_t_java = (*env)->GetObjectArrayElement(env, obss, i + n1 + n2);
            jobject gtime_t_java = (*env)->GetObjectArrayElement(env, obstime, i + n1 + n2);

            // 获取已有数组并原地更新（复用，不分配新数组）
            jdoubleArray pArray = (*env)->GetObjectField(env, obsd_t_java, f_obs_P);
            jdoubleArray lArray = (*env)->GetObjectField(env, obsd_t_java, f_obs_L);
            jfloatArray  dArray = (*env)->GetObjectField(env, obsd_t_java, f_obs_D);
            jfloatArray  snrArray = (*env)->GetObjectField(env, obsd_t_java, f_obs_SNR);
            jintArray    satArray = (*env)->GetObjectField(env, obsd_t_java, f_obs_sat);
            jintArray    codeArray = (*env)->GetObjectField(env, obsd_t_java, f_obs_code);

            if (pArray)   (*env)->SetDoubleArrayRegion(env, pArray, 0, NFREQ + NEXOBS, RTCM->obs.data[i].P);
            if (lArray)   (*env)->SetDoubleArrayRegion(env, lArray, 0, NFREQ + NEXOBS, RTCM->obs.data[i].L);
            if (dArray)   (*env)->SetFloatArrayRegion(env, dArray, 0, NFREQ + NEXOBS, RTCM->obs.data[i].D);
            if (snrArray) (*env)->SetFloatArrayRegion(env, snrArray, 0, NFREQ + NEXOBS, RTCM->obs.data[i].SNR);
            if (satArray) (*env)->SetIntArrayRegion(env, satArray, 0, 1, &satValue);

            if (codeArray) {
                jint codeValues[NFREQ + NEXOBS];
                for (int j = 0; j < NFREQ + NEXOBS; j++) {
                    codeValues[j] = (jint) RTCM->obs.data[i].code[j];
                }
                (*env)->SetIntArrayRegion(env, codeArray, 0, NFREQ + NEXOBS, codeValues);
            }

            (*env)->SetDoubleField(env, gtime_t_java, f_time_time, time);
            (*env)->SetDoubleField(env, gtime_t_java, f_time_sec, sec);

            // 释放本地引用
            (*env)->DeleteLocalRef(env, obsd_t_java);
            (*env)->DeleteLocalRef(env, gtime_t_java);
        }
        return result;
    }
    return result;
}



/* ═══════════════════════════════════════════════════════════════════════
 * RTCM2 JNI 桥接（复用 cached Field ID）
 * ═══════════════════════════════════════════════════════════════════════ */

JNIEXPORT jint JNICALL
Java_com_ecut_pnav_ui_Ntrip_NtripFragment_inputrtcm2(
        JNIEnv *env, jobject thiz, jobject rtcm,
        jbyte data, jobject obss, jobject obstime, jdoubleArray stapos) {
    uint8_t data_uint8 = (uint8_t) data;
    jdouble time = 0;
    jdouble sec = 0;
    jint satValue = 0;
    int result = input_rtcm2(RTCM2, data_uint8);
    if (result > 0) {
        if (stapos != NULL) {
            (*env)->SetDoubleArrayRegion(env, stapos, 0, 3, RTCM2->sta.pos);
        }
        ensureFieldIds(env, obss, obstime);
        for (int i = 0; i < RTCM2->obs.n; i++) {
            satValue = (jint) RTCM2->obs.data[i].sat;
            time = RTCM2->obs.data[i].time.time;
            sec = RTCM2->obs.data[i].time.sec;

            jobject obsd_t_java = (*env)->GetObjectArrayElement(env, obss, i);
            jobject gtime_t_java = (*env)->GetObjectArrayElement(env, obstime, i);

            jdoubleArray pArray = (*env)->GetObjectField(env, obsd_t_java, f_obs_P);
            jdoubleArray lArray = (*env)->GetObjectField(env, obsd_t_java, f_obs_L);
            jfloatArray  dArray = (*env)->GetObjectField(env, obsd_t_java, f_obs_D);
            jfloatArray  snrArray = (*env)->GetObjectField(env, obsd_t_java, f_obs_SNR);
            jintArray    satArray = (*env)->GetObjectField(env, obsd_t_java, f_obs_sat);
            jintArray    codeArray = (*env)->GetObjectField(env, obsd_t_java, f_obs_code);

            if (pArray)   (*env)->SetDoubleArrayRegion(env, pArray, 0, NFREQ + NEXOBS, RTCM2->obs.data[i].P);
            if (lArray)   (*env)->SetDoubleArrayRegion(env, lArray, 0, NFREQ + NEXOBS, RTCM2->obs.data[i].L);
            if (dArray)   (*env)->SetFloatArrayRegion(env, dArray, 0, NFREQ + NEXOBS, RTCM2->obs.data[i].D);
            if (snrArray) (*env)->SetFloatArrayRegion(env, snrArray, 0, NFREQ + NEXOBS, RTCM2->obs.data[i].SNR);
            if (satArray) (*env)->SetIntArrayRegion(env, satArray, 0, 1, &satValue);

            if (codeArray) {
                jint codeValues[NFREQ + NEXOBS];
                for (int j = 0; j < NFREQ + NEXOBS; j++) {
                    codeValues[j] = (jint) RTCM2->obs.data[i].code[j];
                }
                (*env)->SetIntArrayRegion(env, codeArray, 0, NFREQ + NEXOBS, codeValues);
            }

            (*env)->SetDoubleField(env, gtime_t_java, f_time_time, time);
            (*env)->SetDoubleField(env, gtime_t_java, f_time_sec, sec);

            (*env)->DeleteLocalRef(env, obsd_t_java);
            (*env)->DeleteLocalRef(env, gtime_t_java);
        }
        return result;
    }
    return result;
}

JNIEXPORT void JNICALL
Java_com_ecut_pnav_ui_Ntrip_NtripFragment_initrtcm(JNIEnv *env, jobject thiz) {
RTCM = malloc(sizeof(rtcm_t));
if (RTCM == NULL) {
fprintf(stderr, "内存分配失败\n");
exit(EXIT_FAILURE);
}
init_rtcm(RTCM);
}

JNIEXPORT void JNICALL
Java_com_ecut_pnav_ui_Ntrip_NtripFragment_initrtcm2(JNIEnv *env, jobject thiz) {
RTCM2 = malloc(sizeof(rtcm_t));
if (RTCM2 == NULL) {
fprintf(stderr, "RTCM2 内存分配失败\n");
exit(EXIT_FAILURE);
}
init_rtcm(RTCM2);
}

/* ═══════════════════════════════════════════════════════════════════════
 * extractRtcmNav — 从 CORS RTCM 实时星历深拷贝到 Java nav_t
 * ═══════════════════════════════════════════════════════════════════════ */

static jobject jni_new_gtime(JNIEnv *env, gtime_t *ct) {
	jclass cls = (*env)->FindClass(env, "com/rtklib/bean/gtime_t");
	jmethodID init = (*env)->GetMethodID(env, cls, "<init>", "(DD)V");
	jobject obj = (*env)->NewObject(env, cls, init, (jdouble)ct->time, (jdouble)ct->sec);
	(*env)->DeleteLocalRef(env, cls);
	return obj;
}

static jobject jni_new_eph(JNIEnv *env, eph_t *ce) {
	jclass cls = (*env)->FindClass(env, "com/rtklib/bean/eph_t");
	jmethodID init = (*env)->GetMethodID(env, cls, "<init>", "()V");
	jobject obj = (*env)->NewObject(env, cls, init);
	jfieldID fid;

	/* scalar int fields */
	fid=(*env)->GetFieldID(env,cls,"sat","I");(*env)->SetIntField(env,obj,fid,ce->sat);
	fid=(*env)->GetFieldID(env,cls,"iode","I");(*env)->SetIntField(env,obj,fid,ce->iode);
	fid=(*env)->GetFieldID(env,cls,"iodc","I");(*env)->SetIntField(env,obj,fid,ce->iodc);
	fid=(*env)->GetFieldID(env,cls,"sva","I");(*env)->SetIntField(env,obj,fid,ce->sva);
	fid=(*env)->GetFieldID(env,cls,"svh","I");(*env)->SetIntField(env,obj,fid,ce->svh);
	fid=(*env)->GetFieldID(env,cls,"code","I");(*env)->SetIntField(env,obj,fid,ce->code);
	fid=(*env)->GetFieldID(env,cls,"flag","I");(*env)->SetIntField(env,obj,fid,ce->flag);

	/* scalar double fields */
#define SET_D(f) fid=(*env)->GetFieldID(env,cls,#f,"D");(*env)->SetDoubleField(env,obj,fid,ce->f)
	SET_D(A); SET_D(e); SET_D(i0); SET_D(OMG0); SET_D(omg); SET_D(M0);
	SET_D(deln); SET_D(OMGd); SET_D(idot);
	SET_D(crc); SET_D(crs); SET_D(cuc); SET_D(cus); SET_D(cic); SET_D(cis);
	SET_D(toes); SET_D(fit); SET_D(f0); SET_D(f1); SET_D(f2);
	SET_D(Adot); SET_D(ndot);
#undef SET_D

	/* week: C int → Java int[1] */
	jintArray wkArr = (*env)->NewIntArray(env, 1);
	jint wk = (jint)ce->week;
	(*env)->SetIntArrayRegion(env, wkArr, 0, 1, &wk);
	fid = (*env)->GetFieldID(env, cls, "week", "[I");
	(*env)->SetObjectField(env, obj, fid, wkArr);
	(*env)->DeleteLocalRef(env, wkArr);

	/* tgd: C double[6] → Java double[2] */
	jdoubleArray tArr = (*env)->NewDoubleArray(env, 2);
	jdouble t2[2] = {ce->tgd[0], ce->tgd[1]};
	(*env)->SetDoubleArrayRegion(env, tArr, 0, 2, t2);
	fid = (*env)->GetFieldID(env, cls, "tgd", "[D");
	(*env)->SetObjectField(env, obj, fid, tArr);
	(*env)->DeleteLocalRef(env, tArr);

	/* gtime_t 子对象 */
	jobject toeO = jni_new_gtime(env, &ce->toe);
	jobject tocO = jni_new_gtime(env, &ce->toc);
	jobject ttrO = jni_new_gtime(env, &ce->ttr);
	fid = (*env)->GetFieldID(env, cls, "toe", "Lcom/rtklib/bean/gtime_t;");
	(*env)->SetObjectField(env, obj, fid, toeO);
	fid = (*env)->GetFieldID(env, cls, "toc", "Lcom/rtklib/bean/gtime_t;");
	(*env)->SetObjectField(env, obj, fid, tocO);
	fid = (*env)->GetFieldID(env, cls, "ttr", "Lcom/rtklib/bean/gtime_t;");
	(*env)->SetObjectField(env, obj, fid, ttrO);
	(*env)->DeleteLocalRef(env, toeO);
	(*env)->DeleteLocalRef(env, tocO);
	(*env)->DeleteLocalRef(env, ttrO);

	(*env)->DeleteLocalRef(env, cls);
	return obj;
}

static jobject jni_new_geph(JNIEnv *env, geph_t *cg) {
	jclass cls = (*env)->FindClass(env, "com/rtklib/bean/geph_t");
	jmethodID init = (*env)->GetMethodID(env, cls, "<init>", "()V");
	jobject obj = (*env)->NewObject(env, cls, init);
	jfieldID fid;

	fid=(*env)->GetFieldID(env,cls,"sat","I");(*env)->SetIntField(env,obj,fid,cg->sat);
	fid=(*env)->GetFieldID(env,cls,"iode","I");(*env)->SetIntField(env,obj,fid,cg->iode);
	fid=(*env)->GetFieldID(env,cls,"frq","I");(*env)->SetIntField(env,obj,fid,cg->frq);
	fid=(*env)->GetFieldID(env,cls,"svh","I");(*env)->SetIntField(env,obj,fid,cg->svh);
	fid=(*env)->GetFieldID(env,cls,"sva","I");(*env)->SetIntField(env,obj,fid,cg->sva);
	fid=(*env)->GetFieldID(env,cls,"age","I");(*env)->SetIntField(env,obj,fid,cg->age);

	fid=(*env)->GetFieldID(env,cls,"taun","D");(*env)->SetDoubleField(env,obj,fid,cg->taun);
	fid=(*env)->GetFieldID(env,cls,"gamn","D");(*env)->SetDoubleField(env,obj,fid,cg->gamn);
	fid=(*env)->GetFieldID(env,cls,"dtaun","D");(*env)->SetDoubleField(env,obj,fid,cg->dtaun);

	/* pos[3] / vel[3] / acc[3] */
	jdoubleArray pArr = (*env)->NewDoubleArray(env, 3);
	jdoubleArray vArr = (*env)->NewDoubleArray(env, 3);
	jdoubleArray aArr = (*env)->NewDoubleArray(env, 3);
	(*env)->SetDoubleArrayRegion(env, pArr, 0, 3, cg->pos);
	(*env)->SetDoubleArrayRegion(env, vArr, 0, 3, cg->vel);
	(*env)->SetDoubleArrayRegion(env, aArr, 0, 3, cg->acc);
	fid = (*env)->GetFieldID(env, cls, "pos", "[D"); (*env)->SetObjectField(env, obj, fid, pArr);
	fid = (*env)->GetFieldID(env, cls, "vel", "[D"); (*env)->SetObjectField(env, obj, fid, vArr);
	fid = (*env)->GetFieldID(env, cls, "acc", "[D"); (*env)->SetObjectField(env, obj, fid, aArr);
	(*env)->DeleteLocalRef(env, pArr); (*env)->DeleteLocalRef(env, vArr); (*env)->DeleteLocalRef(env, aArr);

	/* gtime_t 子对象 */
	jobject toeO = jni_new_gtime(env, &cg->toe);
	jobject tofO = jni_new_gtime(env, &cg->tof);
	fid = (*env)->GetFieldID(env, cls, "toe", "Lcom/rtklib/bean/gtime_t;");
	(*env)->SetObjectField(env, obj, fid, toeO);
	fid = (*env)->GetFieldID(env, cls, "tof", "Lcom/rtklib/bean/gtime_t;");
	(*env)->SetObjectField(env, obj, fid, tofO);
	(*env)->DeleteLocalRef(env, toeO); (*env)->DeleteLocalRef(env, tofO);

	(*env)->DeleteLocalRef(env, cls);
	return obj;
}

JNIEXPORT jobject JNICALL
Java_com_ecut_pnav_ui_Ntrip_NtripFragment_extractRtcmNav(JNIEnv *env, jclass clazz) {
	jclass navCls = (*env)->FindClass(env, "com/rtklib/bean/nav_t");
	jmethodID navInit = (*env)->GetMethodID(env, navCls, "<init>", "()V");
	jobject navObj = (*env)->NewObject(env, navCls, navInit);
	jfieldID fid;
	int i;

	if (RTCM == NULL) {
		(*env)->DeleteLocalRef(env, navCls);
		return navObj;
	}

	nav_t *cnav = &RTCM->nav;

	/* 统计有效 eph 条目 (sat != 0) */
	int ephAll = (cnav->eph != NULL) ? cnav->n : 0;
	int ephCnt = 0;
	for (i = 0; i < ephAll; i++) {
		if (cnav->eph[i].sat != 0) ephCnt++;
	}

	/* 统计有效 geph 条目 */
	int gephAll = (cnav->geph != NULL) ? cnav->ng : 0;
	int gephCnt = 0;
	for (i = 0; i < gephAll; i++) {
		if (cnav->geph[i].sat != 0) gephCnt++;
	}

	/* 设置 nav_t 计数 */
	fid = (*env)->GetFieldID(env, navCls, "n", "I");
	(*env)->SetIntField(env, navObj, fid, ephCnt);
	fid = (*env)->GetFieldID(env, navCls, "nmax", "I");
	(*env)->SetIntField(env, navObj, fid, ephCnt);
	fid = (*env)->GetFieldID(env, navCls, "ng", "I");
	(*env)->SetIntField(env, navObj, fid, gephCnt);
	fid = (*env)->GetFieldID(env, navCls, "ngmax", "I");
	(*env)->SetIntField(env, navObj, fid, gephCnt);
	fid = (*env)->GetFieldID(env, navCls, "ns", "I");
	(*env)->SetIntField(env, navObj, fid, 0);
	fid = (*env)->GetFieldID(env, navCls, "nsmax", "I");
	(*env)->SetIntField(env, navObj, fid, 0);

	/* 填充 eph[] */
	if (ephCnt > 0 && cnav->eph != NULL) {
		jclass ephCls = (*env)->FindClass(env, "com/rtklib/bean/eph_t");
		jobjectArray ephArr = (*env)->NewObjectArray(env, ephCnt, ephCls, NULL);
		int idx = 0;
		for (i = 0; i < ephAll && idx < ephCnt; i++) {
			if (cnav->eph[i].sat == 0) continue;
			jobject ephObj = jni_new_eph(env, &cnav->eph[i]);
			(*env)->SetObjectArrayElement(env, ephArr, idx++, ephObj);
			(*env)->DeleteLocalRef(env, ephObj);
		}
		fid = (*env)->GetFieldID(env, navCls, "eph", "[Lcom/rtklib/bean/eph_t;");
		(*env)->SetObjectField(env, navObj, fid, ephArr);
		(*env)->DeleteLocalRef(env, ephArr);
		(*env)->DeleteLocalRef(env, ephCls);
	}

	/* 填充 geph[] */
	if (gephCnt > 0 && cnav->geph != NULL) {
		jclass gephCls = (*env)->FindClass(env, "com/rtklib/bean/geph_t");
		jobjectArray gephArr = (*env)->NewObjectArray(env, gephCnt, gephCls, NULL);
		int idx = 0;
		for (i = 0; i < gephAll && idx < gephCnt; i++) {
			if (cnav->geph[i].sat == 0) continue;
			jobject gephObj = jni_new_geph(env, &cnav->geph[i]);
			(*env)->SetObjectArrayElement(env, gephArr, idx++, gephObj);
			(*env)->DeleteLocalRef(env, gephObj);
		}
		fid = (*env)->GetFieldID(env, navCls, "geph", "[Lcom/rtklib/bean/geph_t;");
		(*env)->SetObjectField(env, navObj, fid, gephArr);
		(*env)->DeleteLocalRef(env, gephArr);
		(*env)->DeleteLocalRef(env, gephCls);
	}

	/* 拷贝 glo_fcn[32] */
	jintArray gloArr = (*env)->NewIntArray(env, 32);
	jint gloBuf[32];
	for (i = 0; i < 32; i++) gloBuf[i] = (jint)cnav->glo_fcn[i];
	(*env)->SetIntArrayRegion(env, gloArr, 0, 32, gloBuf);
	fid = (*env)->GetFieldID(env, navCls, "glo_fcn", "[I");
	(*env)->SetObjectField(env, navObj, fid, gloArr);
	(*env)->DeleteLocalRef(env, gloArr);

	(*env)->DeleteLocalRef(env, navCls);
	return navObj;
}

/* ═══════════════════════════════════════════════════════════════════════
 * GNSS Quality Analysis JNI bridge
 * ═══════════════════════════════════════════════════════════════════════ */

#include "gnss_quality.h"

JNIEXPORT void JNICALL
Java_com_ecut_pnav_ui_quality_QualityFragment_qualityInit(
        JNIEnv *env, jobject thiz) {
    quality_init();
}

JNIEXPORT void JNICALL
Java_com_ecut_pnav_ui_quality_QualityFragment_qualityProcessEpoch(
        JNIEnv *env, jobject thiz,
        jintArray j_constellations,
        jintArray j_svids,
        jdoubleArray j_freqs,
        jdoubleArray j_snrs,
        jdoubleArray j_prs,
        jdoubleArray j_adrs,
        jintArray j_adr_states,
        jfloatArray j_elevations,
        jint n,
        jlong j_recv_time_nanos) {

    if (n <= 0) return;

    jint *cons = (*env)->GetIntArrayElements(env, j_constellations, NULL);
    jint *svids = (*env)->GetIntArrayElements(env, j_svids, NULL);
    jdouble *freqs = (*env)->GetDoubleArrayElements(env, j_freqs, NULL);
    jdouble *snrs = (*env)->GetDoubleArrayElements(env, j_snrs, NULL);
    jdouble *prs = (*env)->GetDoubleArrayElements(env, j_prs, NULL);
    jdouble *adrs = (*env)->GetDoubleArrayElements(env, j_adrs, NULL);
    jint *adr_states = (*env)->GetIntArrayElements(env, j_adr_states, NULL);
    jfloat *elevs = (*env)->GetFloatArrayElements(env, j_elevations, NULL);

    if (cons && svids && freqs && snrs && prs && adrs && adr_states) {
        /* Allocate temporary array on heap — safe for typical n <= 200 */
        raw_meas_t *meas = (raw_meas_t *)malloc(n * sizeof(raw_meas_t));
        if (meas) {
            for (int i = 0; i < n; i++) {
                meas[i].constellation    = cons[i];
                meas[i].svid             = svids[i];
                meas[i].carrier_freq_hz  = freqs[i];
                meas[i].snr_db           = snrs[i];
                meas[i].pr_m             = prs[i];
                meas[i].adr_m            = adrs[i];
                meas[i].adr_state        = adr_states[i];
                meas[i].recv_time_nanos  = (int64_t)j_recv_time_nanos;
                meas[i].elevation_deg    = elevs ? elevs[i] : NAN;
            }
            quality_process_epoch(meas, n);
            free(meas);
        }
    }

    /* Release array elements (JNI_ABORT = don't copy back) */
    if (cons)       (*env)->ReleaseIntArrayElements(env, j_constellations, cons, JNI_ABORT);
    if (svids)      (*env)->ReleaseIntArrayElements(env, j_svids, svids, JNI_ABORT);
    if (freqs)      (*env)->ReleaseDoubleArrayElements(env, j_freqs, freqs, JNI_ABORT);
    if (snrs)       (*env)->ReleaseDoubleArrayElements(env, j_snrs, snrs, JNI_ABORT);
    if (prs)        (*env)->ReleaseDoubleArrayElements(env, j_prs, prs, JNI_ABORT);
    if (adrs)       (*env)->ReleaseDoubleArrayElements(env, j_adrs, adrs, JNI_ABORT);
    if (adr_states) (*env)->ReleaseIntArrayElements(env, j_adr_states, adr_states, JNI_ABORT);
    if (elevs)      (*env)->ReleaseFloatArrayElements(env, j_elevations, elevs, JNI_ABORT);
}

JNIEXPORT jstring JNICALL
Java_com_ecut_pnav_ui_quality_QualityFragment_qualityGetReport(
        JNIEnv *env, jobject thiz) {

    quality_report_t report;
    quality_get_report(&report);

    /* Build JSON string — base fields (without closing brace) */
    static char buf[65536];
    int pos = snprintf(buf, sizeof(buf),
        "{"
        "\"snr_mean\":[%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f],"
        "\"snr_bins_gps\":[%d,%d,%d,%d],"
        "\"snr_bins_bds\":[%d,%d,%d,%d],"
        "\"snr_bins_glo\":[%d,%d,%d,%d],"
        "\"snr_bins_gal\":[%d,%d,%d,%d],"
        "\"sat_total\":%d,"
        "\"sat_gps\":%d,\"sat_bds\":%d,\"sat_glo\":%d,\"sat_gal\":%d,"
        "\"cmc_rms\":[%.3f,%.3f,%.3f,%.3f],"
        "\"gf_slips\":[%d,%d,%d,%d],"
        "\"mw_slips\":[%d,%d,%d,%d],"
        "\"gf_poly_slips\":[%d,%d,%d,%d],"
        "\"slip_combined\":[%d,%d,%d,%d],"
        "\"mp1_rms\":[%.3f,%.3f,%.3f,%.3f],"
        "\"mp2_rms\":[%.3f,%.3f,%.3f,%.3f],"
        "\"mp5_rms\":[%.3f,%.3f,%.3f,%.3f],"
        "\"mp1_sw_rms\":[%.3f,%.3f,%.3f,%.3f],"
        "\"mp2_sw_rms\":[%.3f,%.3f,%.3f,%.3f],"
        "\"mp5_sw_rms\":[%.3f,%.3f,%.3f,%.3f],"
        "\"td_pr_noise_rms\":[%.3f,%.3f,%.3f,%.3f],"
        "\"td_cp_noise_rms\":[%.4f,%.4f,%.4f,%.4f],"
        "\"ion_rate_mean\":[%.4f,%.4f,%.4f,%.4f],"
        "\"ion_rate_k2_mean\":[%.4f,%.4f,%.4f,%.4f],"
        "\"elev_bins_gps\":[%d,%d,%d,%d],"
        "\"elev_bins_bds\":[%d,%d,%d,%d],"
        "\"elev_bins_glo\":[%d,%d,%d,%d],"
        "\"elev_bins_gal\":[%d,%d,%d,%d],"
        "\"completeness\":[%.3f,%.3f,%.3f,%.3f],"
        "\"avg_sats\":[%.1f,%.1f,%.1f,%.1f],"
        "\"dual_freq_ratio\":[%.3f,%.3f,%.3f,%.3f],"
        "\"csr\":[%.1f,%.1f,%.1f,%.1f],"
        "\"epoch_count\":%d,"
        "\"total_obs\":%d,"
        "\"has_dual_freq\":%d,"
        "\"has_adr\":%d"
        ,

        /* SNR means — indices: 1=GPS, 3=GLO, 5=BDS, 6=GAL, others 0 */
        report.snr_mean[1], report.snr_mean[5], report.snr_mean[3], report.snr_mean[6],
        NAN, NAN, NAN,

        /* SNR bins per system */
        report.snr_bins[1][0], report.snr_bins[1][1], report.snr_bins[1][2], report.snr_bins[1][3],
        report.snr_bins[5][0], report.snr_bins[5][1], report.snr_bins[5][2], report.snr_bins[5][3],
        report.snr_bins[3][0], report.snr_bins[3][1], report.snr_bins[3][2], report.snr_bins[3][3],
        report.snr_bins[6][0], report.snr_bins[6][1], report.snr_bins[6][2], report.snr_bins[6][3],

        report.sat_total, report.sat_count[1], report.sat_count[5],
        report.sat_count[3], report.sat_count[6],
        /* CMC RMS */
        report.cmc_rms[1], report.cmc_rms[5], report.cmc_rms[3], report.cmc_rms[6],

        /* GF slips */
        report.gf_slip_count[1], report.gf_slip_count[5],
        report.gf_slip_count[3], report.gf_slip_count[6],

        /* MW slips */
        report.mw_slip_count[1], report.mw_slip_count[5],
        report.mw_slip_count[3], report.mw_slip_count[6],

        /* GF poly slips */
        report.gf_poly_slip_count[1], report.gf_poly_slip_count[5],
        report.gf_poly_slip_count[3], report.gf_poly_slip_count[6],

        /* Combined slips */
        report.slip_combined[1], report.slip_combined[5],
        report.slip_combined[3], report.slip_combined[6],

        /* MP1 RMS */
        report.mp1_rms[1], report.mp1_rms[5], report.mp1_rms[3], report.mp1_rms[6],
        /* MP2 RMS */
        report.mp2_rms[1], report.mp2_rms[5], report.mp2_rms[3], report.mp2_rms[6],
        /* MP5 RMS */
        report.mp5_rms[1], report.mp5_rms[5], report.mp5_rms[3], report.mp5_rms[6],
        /* MP1 SW RMS */
        report.mp1_sw_rms[1], report.mp1_sw_rms[5], report.mp1_sw_rms[3], report.mp1_sw_rms[6],
        /* MP2 SW RMS */
        report.mp2_sw_rms[1], report.mp2_sw_rms[5], report.mp2_sw_rms[3], report.mp2_sw_rms[6],
        /* MP5 SW RMS */
        report.mp5_sw_rms[1], report.mp5_sw_rms[5], report.mp5_sw_rms[3], report.mp5_sw_rms[6],

        /* TD PR noise RMS */
        report.td_pr_noise_rms[1], report.td_pr_noise_rms[5],
        report.td_pr_noise_rms[3], report.td_pr_noise_rms[6],

        /* TD CP noise RMS (cycles) */
        report.td_cp_noise_rms[1], report.td_cp_noise_rms[5],
        report.td_cp_noise_rms[3], report.td_cp_noise_rms[6],

        /* Ion rate mean */
        report.ion_rate_mean[1], report.ion_rate_mean[5],
        report.ion_rate_mean[3], report.ion_rate_mean[6],

        /* Ion rate K2 mean */
        report.ion_rate_k2_mean[1], report.ion_rate_k2_mean[5],
        report.ion_rate_k2_mean[3], report.ion_rate_k2_mean[6],

        /* Elevation bins */
        report.elev_bins[1][0], report.elev_bins[1][1], report.elev_bins[1][2], report.elev_bins[1][3],
        report.elev_bins[5][0], report.elev_bins[5][1], report.elev_bins[5][2], report.elev_bins[5][3],
        report.elev_bins[3][0], report.elev_bins[3][1], report.elev_bins[3][2], report.elev_bins[3][3],
        report.elev_bins[6][0], report.elev_bins[6][1], report.elev_bins[6][2], report.elev_bins[6][3],

        /* Completeness */
        report.completeness[1], report.completeness[5],
        report.completeness[3], report.completeness[6],

        /* Avg sats */
        report.avg_sats_per_epoch[1], report.avg_sats_per_epoch[5],
        report.avg_sats_per_epoch[3], report.avg_sats_per_epoch[6],

        /* Dual-freq ratio */
        report.dual_freq_ratio[1], report.dual_freq_ratio[5],
        report.dual_freq_ratio[3], report.dual_freq_ratio[6],

        /* CSR */
        report.csr[1], report.csr[5], report.csr[3], report.csr[6],

        report.epoch_count, report.total_obs, report.has_dual_freq, report.has_adr
    );

    /* Append per-satellite info (FDE) */
    if (pos < (int)sizeof(buf) - 4) {
        pos += snprintf(buf + pos, sizeof(buf) - pos, ",\"sat_info\":[");
        for (int i = 0; i < report.sat_info_count && pos < (int)sizeof(buf) - 80; i++) {
            sat_info_t *si = &report.sat_info[i];
            pos += snprintf(buf + pos, sizeof(buf) - pos,
                "%s{\"c\":%d,\"s\":%d,\"cmc\":%.3f,\"snr\":%.1f,\"n\":%d}",
                i > 0 ? "," : "",
                si->cons, si->svid, si->cmc_rms, si->snr_mean, si->sample_count);
        }
        pos += snprintf(buf + pos, sizeof(buf) - pos, "]");
    }

    /* Close JSON */
    if (pos < (int)sizeof(buf) - 2) {
        pos += snprintf(buf + pos, sizeof(buf) - pos, "}");
    } else {
        return (*env)->NewStringUTF(env, "{\"epoch_count\":0,\"has_dual_freq\":0}");
    }

    return (*env)->NewStringUTF(env, buf);
}

JNIEXPORT void JNICALL
Java_com_ecut_pnav_ui_quality_QualityFragment_qualityFree(
        JNIEnv *env, jobject thiz) {
    quality_free();
}

JNIEXPORT jstring JNICALL
Java_com_ecut_pnav_ui_quality_QualityFragment_qualityGetTimeSeries(
        JNIEnv *env, jobject thiz) {
    static char buf[98304];
    int n = quality_get_timeseries_json(buf, sizeof(buf));
    if (n < 0) {
        return (*env)->NewStringUTF(env, "{}");
    }
    return (*env)->NewStringUTF(env, buf);
}