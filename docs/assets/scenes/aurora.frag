// aurora — tools/site/agsl2glsl.mjs 가 app/src/main/assets/scenes/_lib.agsl + aurora.agsl 에서 만든 파일. 손으로 고치지 말 것.
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform vec2 uSize;
uniform float uTime;
uniform float uFlow;
uniform float uDay;
uniform float uWind;
uniform float uRain;
uniform float uLevel;
uniform float uSoft;
uniform float uSeed;
uniform float uGrade;
float sat(float x){return clamp(x,0.0,1.0);}
float sq(float x){return x*x;}
vec3 hx(float r,float g,float b){return vec3(r,g,b)/255.0;}
float h11(float n){return fract(sin(n*127.1+311.7)*43758.5453);}
float h21(vec2 p){
p=fract(p*vec2(123.34,456.21));
p+=dot(p,p+45.32);
return fract(p.x*p.y);
}
vec2 h22(vec2 p){float n=h21(p);return vec2(n,h21(p+n*31.7+17.17));}
float vn(vec2 p){
vec2 i=floor(p);
vec2 f=fract(p);
vec2 u=f*f*(3.0-2.0*f);
float a=h21(i);
float b=h21(i+vec2(1.0,0.0));
float c=h21(i+vec2(0.0,1.0));
float d=h21(i+vec2(1.0,1.0));
return mix(mix(a,b,u.x),mix(c,d,u.x),u.y);
}
float fbm(vec2 p){
float v=0.0;
float a=0.5;
for(int i=0;i<5;i++){v+=a*vn(p);p=p*2.03+vec2(13.7,7.1);a*=0.5;}
return v;
}
float fbm3(vec2 p){
float v=0.0;
float a=0.5;
for(int i=0;i<3;i++){v+=a*vn(p);p=p*2.03+vec2(13.7,7.1);a*=0.5;}
return v/0.875;
}
mat2 rot(float a){float c=cos(a);float s=sin(a);return mat2(c,-s,s,c);}
float dayK(){float t=mod(uDay,4.0);float f=fract(t);return floor(t)+f*f*(3.0-2.0*f);}
vec3 day4(vec3 a,vec3 b,vec3 c,vec3 d){
float t=dayK();
if(t<1.0)return mix(a,b,t);
if(t<2.0)return mix(b,c,t-1.0);
if(t<3.0)return mix(c,d,t-2.0);
return mix(d,a,t-3.0);
}
float day4f(float a,float b,float c,float d){
float t=dayK();
if(t<1.0)return mix(a,b,t);
if(t<2.0)return mix(b,c,t-1.0);
if(t<3.0)return mix(c,d,t-2.0);
return mix(d,a,t-3.0);
}
float gust(float x,float t){
float g=vn(vec2(x*0.9-t*0.35,t*0.07))*0.65+vn(vec2(x*2.3-t*0.8,3.7))*0.35;
return sat(g*(0.55+0.9*uWind));
}
float stars(vec2 p,float k){
vec2 g=p*k;
vec2 id=floor(g);
vec2 f=fract(g)-0.5;
float r=h21(id+3.1);
if(r<0.80)return 0.0;
vec2 o=(h22(id+9.7)-0.5)*0.7;
float d=length(f-o);
float tw=0.55+0.45*sin(uTime*(0.6+2.2*h21(id+1.3))+r*40.0);
float s=(r-0.80)*5.0;
return smoothstep(0.09*(0.4+s),0.0,d)*tw*(0.35+0.65*s);
}
float glow(float d,float r){return exp(-d*d/(r*r));}
vec4 finish(vec3 col,vec2 xy){
vec2 uv=xy/uSize;
float l=dot(col,vec3(0.299,0.587,0.114));
if(uGrade>1.5){
float k=smoothstep(0.02,0.92,l);
vec3 ink=mix(vec3(0.085,0.078,0.072),vec3(0.80,0.77,0.71),k*k*(1.6-0.6*k));
col=mix(ink,col,0.10);
}else if(uGrade>0.5){
col=mix(col,vec3(l),0.10);
col=col*vec3(1.03,1.0,0.95)+vec3(0.028,0.022,0.034);
}
float g=uGrade>0.5?0.060:0.020;
float n=h21(xy+fract(uTime*0.37)*91.0)-0.5;
col+=vec3(n*g);
vec2 v=uv-0.5;
col*=1.0-0.26*dot(v,v);
return vec4(clamp(col,vec3(0.0),vec3(1.0)),1.0);
}
const float TAU=6.2831853;
const float WRAP=62.83185;
float spin(float w){return mod(uTime*w,TAU);}
vec3 day4v(vec3 a,vec3 b,vec3 c,vec3 d){
float t=dayK();
vec3 x=a;
vec3 y=b;
float f=t;
if(t>=3.0){x=d;y=a;f=t-3.0;}
else if(t>=2.0){x=c;y=d;f=t-2.0;}
else if(t>=1.0){x=b;y=c;f=t-1.0;}
vec3 m=mix(x,y,f);
float cx=max(x.r,max(x.g,x.b))-min(x.r,min(x.g,x.b));
float cy=max(y.r,max(y.g,y.b))-min(y.r,min(y.g,y.b));
float cm=max(m.r,max(m.g,m.b))-min(m.r,min(m.g,m.b));
float k=min(mix(cx,cy,f)/max(cm,0.03),3.0);
float l=dot(m,vec3(0.299,0.587,0.114));
return clamp(vec3(l)+(m-vec3(l))*k,0.0,1.0);
}
float hair(float d,float w){float x=d/w;return 1.0/sq(1.0+x*x);}
vec4 lay(vec2 p,vec2 o,float al,float kp,float drift,float px){
vec2 dir=vec2(cos(al),sin(al));
float u=dot(p-o,dir);
float v=dot(p-o,vec2(-dir.y,dir.x));
float uc=u;
if(u<-0.78)uc=-0.78-0.35*(1.0-exp((u+0.78)/0.35));
if(u>0.90)uc=0.90+0.35*(1.0-exp((0.90-u)/0.35));
float z=exp(-kp*uc);
float zEnd=u<0.0?exp(kp*1.13):exp(-kp*1.25);
return vec4((1.0/z-1.0)/kp+(u-uc)/zEnd+drift,v/z,px/z,z);
}
float mid(float U,float sd){
return 0.40*sin(1.9*U+sd*1.7+spin(0.030))
+0.21*sin(3.3*U+sd*4.1+spin(0.090))
+0.06*sin(5.9*U+sd*2.9+spin(0.220));
}
vec3 silk(vec2 q,float pxl,float sd,float W,float blur,float lines,float motes,float wind,vec3 cb,vec3 cl,vec3 cs,vec3 ck){
float U=q.x;
float cen=W*mid(U,sd);
float A0=W*0.95*sin(1.3*U+sd*5.3+spin(0.026));
float A1=W*0.36*sin(0.9*U+sd*3.3+spin(0.027));
float A2=W*0.29*(0.55+0.45*sin(2.3*U+sd*0.7+spin(0.061)))*(0.88+0.30*wind);
float A3=W*0.064*(0.6+0.4*sin(3.1*U+sd*8.3+spin(0.110)))*(0.6+0.8*wind);
float ext=abs(A0)+abs(A1)+A2+A3;
float glowAlong=0.58+0.72*sq(0.5+0.5*sin(0.7*U+sd*1.1+spin(0.019)));
float wb=0.10*W+blur;
float dc=q.y-cen;
float hw=ext*0.9+0.45*W;
vec3 halo=(cb+0.5*cl+0.5*cs)*(0.022*glowAlong*exp(-sq(dc/hw)));
if(abs(dc)>ext+4.0*wb+0.12*W)return halo;
float k1=2.0;
float k2=4.6;
float k3=9.7;
float psi1=0.7*U+sd*2.1+spin(0.035);
float psi2=1.9*U+sd*6.7+spin(0.115);
float psi3=4.3*U+sd*4.9+spin(0.300);
float ds=2.0/30.0;
float s0=-1.0+0.5*ds;
float a1=k1*s0+psi1;
float S1=sin(a1);
float C1=cos(a1);
float a2=k2*s0+psi2;
float S2=sin(a2);
float C2=cos(a2);
float a3=k3*s0+psi3;
float S3=sin(a3);
float C3=cos(a3);
float r1c=cos(k1*ds);
float r1s=sin(k1*ds);
float r2c=cos(k2*ds);
float r2s=sin(k2*ds);
float r3c=cos(k3*ds);
float r3s=sin(k3*ds);
float wmin=max(blur,1.3*pxl);
float lw=max(1.20*pxl,blur*0.40);
float lamp=lines*1.20*pxl/lw;
float dl=ds/3.0;
float gsoft=0.22*abs(A0)+0.03*W;
vec3 fr=(cl-cs)*0.050;
vec3 acc=vec3(0.0);
float white=0.0;
for(int i=0;i<30;i++){
float fi=float(i);
float s=s0+ds*fi;
float hgt=A1*S1+A2*S2+A3*S3;
float slope=A1*k1*C1+A2*k2*C2+A3*k3*C3;
float vi=cen+A0*s+hgt;
float g=A0+slope;
float g2=-(A1*k1*k1*S1+A2*k2*k2*S2+A3*k3*k3*S3);
float d=q.y-vi;
float ag=abs(g)*ds;
float w=max(ag,wmin);
float e=d/w;
float s2=s*s;
float edge=1.0-s2*s2;
float r=fract(fi*0.618034+sd*0.37);
float rb=fract(fi*0.754878+sd*0.53);
float rc=fract(fi*0.569840+sd*0.71);
float fq=floor(18.0+40.0*r)*0.1;
float sw=0.5+0.5*sin(fq*U+r*40.0+spin(0.15+0.25*r));
float sh=0.60+0.40*sw;
float run=(0.30+1.35*sw*sw*sw)*(0.85+0.35*wind);
float sl=slope/W;
float sheen=exp(-sq((sl-0.30)/0.28));
float dens=ds*W/w;
float ke=exp(-e*e)*dens*edge;
float body=ke*(0.34+1.35*sheen);
float thin=sat(ag/(6.6*pxl));
float j0=(r-0.5)*0.5*dl;
float j1=dl*(0.65+0.7*rb);
float j2=dl*(0.65+0.7*rc);
float ln=hair(d-g*j0-0.5*g2*j0*j0,lw)*(0.15+0.85*r*r)
+hair(d-g*j1-0.5*g2*j1*j1,lw)*(0.10+0.55*rb*rb)
+hair(d+g*j2-0.5*g2*j2*j2,lw)*(0.10+0.55*rc*rc);
ln*=thin*run*edge*(0.65+0.7*sheen);
float bl=hair(d,wb)*edge*min(dens,5.0);
float tc=clamp(sl*1.5,-1.0,1.0);
vec3 front=cb+(cl-cb)*max(tc,0.0)+(cs-cb)*max(-tc,0.0);
float side=smoothstep(-1.0,1.0,g/gsoft);
vec3 ci=mix(ck,front,side);
acc+=ci*(body*0.105*(0.75+0.25*sh)+bl*0.032+ln*lamp*0.40)+fr*(ke*e);
white+=ln*lamp*0.16;
if(motes>0.0&&rb>0.80){
float uu=U*0.95493+mod(uTime*(0.010+0.022*r)+r*60.0,60.0);
float id=mod(floor(uu),60.0);
float f=fract(uu);
float hm=h11(id*1.37+fi*7.3+sd);
float off=0.2+0.6*h11(id*3.11+fi*1.7+sd);
float du=(f-off)/0.95493;
float dm=d-(h11(id+fi*3.3)-0.5)*8.0*pxl;
float rr=du*du+dm*dm;
float tw=0.65+0.35*sin(spin(0.5+0.6*hm)+hm*30.0);
float mr=1.35*pxl+blur*0.6;
float mo=exp(-rr/(mr*mr))*(1.35*pxl/mr)+0.10*exp(-rr/sq(5.0*pxl+blur));
float tail=exp(-sq(dm/(0.9*pxl+blur*0.4)))*exp(-max(du,0.0)/0.02)*step(0.0,du)*0.20;
float on=step(0.5,hm)*motes*tw*edge;
white+=(mo+tail)*on*0.8;
acc+=ci*(mo+tail)*on*0.5;
}
float nS1=S1*r1c+C1*r1s;
C1=C1*r1c-S1*r1s;
S1=nS1;
float nS2=S2*r2c+C2*r2s;
C2=C2*r2c-S2*r2s;
S2=nS2;
float nS3=S3*r3c+C3*r3s;
C3=C3*r3c-S3*r3s;
S3=nS3;
}
for(int j=0;j<4;j++){
float fj=float(j);
float sj=(fj<1.5?-1.0:1.0)*(1.10+0.19*fract(fj*0.5+0.25)*2.0+0.05*sd);
float vj=cen+A0*sj+A1*sin(k1*sj+psi1)+A2*sin(k2*sj+psi2)+A3*sin(k3*sj+psi3);
float rj=fract(fj*0.618034+sd*0.29);
float fqj=floor(14.0+30.0*rj)*0.1;
float swj=0.5+0.5*sin(fqj*U+rj*50.0+spin(0.12+0.2*rj));
float lj=hair(q.y-vj,lw)*(0.10+0.75*swj*swj)*(0.35+0.4*rj);
acc+=mix(cb,cl,0.5)*lj*lamp*0.40;
white+=lj*lamp*0.12;
}
return(max(acc,vec3(0.0))+vec3(white))*glowAlong+halo;
}
vec3 veil(vec2 q,float sd,float W,float blur,vec3 ca,vec3 cb){
float U=q.x;
float cen=W*(0.26*sin(1.7*U+sd*1.3+spin(0.023))+0.12*sin(3.1*U+sd*3.7+spin(0.067)));
float A0=W*(0.35+0.55*(0.5+0.5*sin(2.3*U+sd*5.1+spin(0.029))));
float A1=W*0.40*sin(1.1*U+sd*2.3+spin(0.021));
float A2=W*0.16*(0.5+0.5*sin(2.9*U+sd*0.9+spin(0.043)));
float ext=A0+abs(A1)+A2;
float dc=q.y-cen;
if(abs(dc)>ext+3.0*blur)return vec3(0.0);
float k1=2.0;
float k2=4.6;
float ds=2.0/12.0;
float s0=-1.0+0.5*ds;
float a1=k1*s0+0.9*U+sd*2.7+spin(0.070);
float S1=sin(a1);
float C1=cos(a1);
float a2=k2*s0+2.1*U+sd*6.1+spin(0.110);
float S2=sin(a2);
float C2=cos(a2);
float r1c=cos(k1*ds);
float r1s=sin(k1*ds);
float r2c=cos(k2*ds);
float r2s=sin(k2*ds);
vec3 acc=vec3(0.0);
for(int i=0;i<12;i++){
float s=s0+ds*float(i);
float vi=cen+A0*s+A1*S1+A2*S2;
float g=A0+A1*k1*C1+A2*k2*C2;
float w=max(abs(g)*ds,blur);
float e=(q.y-vi)/w;
float s2=s*s;
float body=exp(-e*e)*ds*W/w*(1.0-s2*s2);
acc+=mix(ca,cb,0.5+0.5*s)*body;
float nS1=S1*r1c+C1*r1s;
C1=C1*r1c-S1*r1s;
S1=nS1;
float nS2=S2*r2c+C2*r2s;
C2=C2*r2c-S2*r2s;
S2=nS2;
}
return acc;
}
vec4 sceneMain(vec2 xy){
vec2 uv=xy/uSize;
float asp=uSize.x/uSize.y;
vec2 p=vec2(uv.x*asp,uv.y);
float px=1.0/uSize.y;
float t=uTime;
vec3 bgTop=day4(hx(28.0,17.0,38.0),hx(6.0,26.0,44.0),hx(24.0,8.0,30.0),hx(5.0,6.0,16.0));
vec3 bgLow=day4(hx(54.0,31.0,60.0),hx(8.0,52.0,70.0),hx(48.0,14.0,46.0),hx(10.0,13.0,38.0));
vec3 col=mix(bgTop,bgLow,smoothstep(0.0,1.0,uv.y));
vec3 cA=day4v(hx(255.0,104.0,142.0),hx(20.0,198.0,218.0),hx(245.0,56.0,150.0),hx(36.0,92.0,255.0));
vec3 cB=day4v(hx(255.0,184.0,124.0),hx(110.0,255.0,190.0),hx(255.0,128.0,84.0),hx(40.0,220.0,255.0));
vec3 cC=day4v(hx(166.0,128.0,255.0),hx(60.0,140.0,255.0),hx(140.0,76.0,250.0),hx(120.0,70.0,255.0));
vec3 cD=day4v(hx(255.0,210.0,160.0),hx(120.0,235.0,255.0),hx(255.0,176.0,104.0),hx(230.0,60.0,220.0));
float dk=fract(dayK());
float between=4.0*dk*(1.0-dk);
cB=mix(cB,mix(cA,vec3(1.0),0.45),0.7*between);
cD=mix(cD,mix(cC,vec3(1.0),0.35),0.7*between);
float gain=day4f(0.78,0.80,0.84,1.0);
float gs=gust(asp-p.x,t);
float fine=smoothstep(180.0,640.0,uSize.y);
vec3 light=vec3(0.0);
vec4 f1=lay(p,vec2(0.52*asp,0.825),-0.22,0.60,mod(t*0.034+uFlow*0.55+uSeed*7.3,WRAP),px);
float g1=0.5+0.5*sin(1.1*f1.x+0.6+uSeed);
float far1=smoothstep(0.46,0.80,f1.w)*(1.0-0.50*smoothstep(1.15,1.60,f1.w));
float W1=0.170*(1.0+0.055*uLevel);
vec2 qb=vec2(f1.x,f1.y-0.65*W1*mid(f1.x,1.0)-0.03);
light+=veil(qb,3.0,0.19,0.055,mix(cA,cC,g1*0.7)*0.9,mix(cC,cA,g1*0.6))*0.085*(1.0+0.6*uLevel)*far1;
float blur1=0.0004+0.0016*uSoft+0.022*sq(max(f1.w-1.12,0.0));
vec3 hero=silk(f1.xy,f1.z,1.0,W1,blur1,1.0-0.45*uSoft,(1.0-0.6*uSoft)*fine,gs,
mix(cA,cC,g1*0.55),mix(cB,cA,g1*0.25),mix(cC,cD,g1*0.6),mix(cC,cD,0.35+0.4*g1));
light+=hero*far1;
vec2 dg=vec2(f1.x*7.002817,f1.y*7.0);
vec2 di=floor(dg);
di.x=mod(di.x,440.0);
vec2 dr=h22(di+3.7);
vec2 df=(fract(dg)-0.15-0.7*dr)/7.0;
float dsz=(0.75+1.0*h21(di+9.1))*f1.z+blur1*0.5;
float dtw=0.6+0.4*sin(spin(0.3+0.5*dr.x)+dr.y*40.0);
float dust=exp(-dot(df,df)/(dsz*dsz))*(1.3*f1.z/dsz)*step(0.62,h21(di+1.9))*dtw*exp(-sq(f1.y/0.30));
light+=mix(vec3(1.0),cB,0.35)*dust*0.30*far1*(1.0-0.6*uSoft)*fine;
vec4 f2=lay(p,vec2(0.50*asp,0.895),-0.09,0.60,mod(t*0.044+uFlow*0.80+uSeed*5.9+20.0,WRAP),px);
float g2=0.5+0.5*sin(1.3*f2.x+2.9+uSeed);
float blur2=0.0004+0.0016*uSoft+0.016*sq(max(f2.w-1.10,0.0));
vec3 side=silk(f2.xy,f2.z,2.0,0.080,blur2,0.9-0.4*uSoft,0.0,gs,
mix(cB,cA,0.30+0.25*g2),mix(cB,vec3(1.0),0.35),mix(cC,cA,g2*0.5),mix(cD,cC,g2*0.4));
float veiled=1.0-0.45*sat(1.6*max(hero.r,max(hero.g,hero.b))*far1);
light+=side*smoothstep(0.42,0.75,f2.w)*(1.0-0.55*smoothstep(1.15,1.75,f2.w))*0.70*veiled;
light*=mix(0.08,1.0,smoothstep(0.39,0.61,uv.y));
light*=(1.0+0.22*uLevel)*(1.0-0.24*uSoft)*(0.93+0.16*gs);
light*=1.8*gain;
float lm=max(light.r,max(light.g,light.b));
vec3 lit=light*((1.0-exp(-lm))/max(lm,0.0001));
lit=mix(lit,1.0-exp(-light),0.38);
col+=lit*(1.0-col*0.6);
return finish(col,xy);
}
uniform float uDim;
uniform vec4 uVeil;
uniform float uFade;
void main() {
    vec4 c = sceneMain(vec2(gl_FragCoord.x, uSize.y - gl_FragCoord.y));
    gl_FragColor = vec4(mix(c.rgb, uVeil.rgb, uVeil.a) * (1.0 - uDim), uFade);
}
