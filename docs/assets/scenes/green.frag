// green — tools/site/agsl2glsl.mjs 가 app/src/main/assets/scenes/_lib.agsl + green.agsl 에서 만든 파일. 손으로 고치지 말 것.
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
const float HOR=0.635;
const float FLOWK=0.50;
const float TREE_W=2.6;
const float TREE_X=0.18;
const float TREE_R=0.050;
vec4 layerP(float fi){
if(fi<0.5)return vec4(0.642,0.018,1.30,7.0);
if(fi<1.5)return vec4(0.662,0.027,1.00,4.8);
if(fi<2.5)return vec4(0.690,0.036,0.78,3.3);
if(fi<3.5)return vec4(0.730,0.046,0.62,2.3);
if(fi<4.5)return vec4(0.796,0.056,0.50,1.55);
return vec4(0.912,0.056,0.40,1.0);
}
float bladeLen(float fi){return 0.018*pow(0.60,5.0-fi);}
float treeDx(float c){
float xs=c+uFlow*FLOWK/2.3-TREE_X*uSize.x/uSize.y+TREE_W*0.5;
float id=floor(xs/TREE_W);
float jit=(h11(mod(id,97.0)*1.37)-h11(0.0))*0.7;
return xs-(id+0.5)*TREE_W-jit;
}
float ridgeY(float c,float fi){
vec4 lp=layerP(fi);
float X=(c+uFlow*FLOWK/lp.w)*lp.z+fi*3.71+0.4;
float w=X+0.10*sin(X*2.9+fi*1.7);
float am=0.35+0.55*vn(vec2(X*0.45+fi*3.1,fi+0.5));
float h=am*sin(w*6.2832+fi*2.1)+0.28*sin(w*2.63+fi*5.3)+0.20*sin(w*13.7+fi*4.3)+0.08*sin(w*29.3+fi*7.9)+0.035*sin(w*61.0+fi*3.3);
float y=lp.x-lp.y*h;
if(fi>2.5&&fi<3.5){
float dx=treeDx(c);
y-=0.018*exp(-dx*dx/0.03);
}
return y;
}
vec3 bladeRow(float xb,float en,float sd,float aa,float dens){
float cov=0.0;
float tipv=0.0;
float tone=0.5;
for(int k=0;k<3;k++){
float cid=floor(xb)+float(k)-1.0;
float r1=h21(vec2(mod(cid,512.0),sd));
float r2=fract(r1*7.13+0.31);
float e=en/(0.40+0.60*r1);
float a=sat((0.52*pow(max(1.0-e,0.0),0.8)-abs(xb-cid-0.5-(r2-0.5)*0.6))/aa+0.5)*step(e,1.0)*step(0.0,en)*step(fract(r1*17.71+0.57),dens);
if(a>cov){cov=a;tipv=e;tone=r2;}
}
return vec3(cov,tipv,tone);
}
float daySeed(){return fract(uSeed*0.61803);}
vec4 hillShade(float c,float y,float fi,float ry,float ryNear,float t,float px,float calm){
vec4 lp=layerP(fi);
float z=lp.w;
float nr=fi*0.2;
float xs=c+uFlow*FLOWK/z;
float wx=xs*z;
float hb=bladeLen(fi);
float d0=y-ry;
float dd=max(d0,0.0);
float wz=z*(1.0-min(dd*3.6,0.62));
float v=sat(dd/max(ryNear-ry,0.035));
vec3 gSha=day4(hx(66.0,112.0,118.0),hx(20.0,104.0,96.0),hx(70.0,70.0,98.0),hx(3.0,18.0,27.0));
vec3 gMid=day4(hx(110.0,170.0,146.0),hx(72.0,176.0,96.0),hx(122.0,128.0,78.0),hx(9.0,44.0,52.0));
vec3 gLit=day4(hx(204.0,226.0,180.0),hx(178.0,228.0,104.0),hx(246.0,190.0,96.0),hx(30.0,94.0,96.0));
vec3 sheenC=day4(hx(244.0,240.0,220.0),hx(236.0,250.0,170.0),hx(255.0,228.0,152.0),hx(116.0,196.0,190.0));
vec3 hazeC=day4(hx(240.0,220.0,220.0),hx(172.0,224.0,216.0),hx(244.0,172.0,152.0),hx(24.0,74.0,90.0));
float sx=day4f(-1.0,-0.6,1.0,0.5);
float sl=(ridgeY(c+0.012,fi)-ry)/0.012;
float lam=sat(0.5+sl*sx*2.4);
float blot=fbm3(vec2(wx*0.9+fi*5.0,wz*2.4));
float cs=smoothstep(0.46,0.70,fbm3(vec2(wx*0.30-t*0.016+daySeed()*30.0,wz*0.55+4.0)));
float ga=0.40+0.75*gust(wx*0.8+wz*0.5,t*0.62);
float ph=(wx-0.55*wz)*2.4-mod(t*0.62,289.0)+(blot-0.5)*1.1+fi*0.37;
float wid=mod(floor(ph),289.0);
float wfr=fract(ph);
float wamp=smoothstep(0.36,0.72,vn(vec2(wid*3.7,(wz+0.5*wx)*1.5+wid*1.3)))*ga;
float band=pow(smoothstep(0.0,0.66,wfr),1.7)*smoothstep(1.0,0.66,wfr)*wamp;
float bent=smoothstep(0.0,0.60,wfr)*smoothstep(1.0,0.80,wfr)*wamp;
float cov=sat(d0/px+0.5);
float dab=0.0;
float flw=0.0;
vec3 flwC=vec3(1.0);
if(fi>2.5){
float th=hb*0.5;
float cellW=hb*0.26;
float F=1.0/cellW;
float d=d0+(vn(vec2(xs*F*0.05,fi))-0.5)*th*1.4;
float j0=floor(d/th);
float e1=(j0+1.0)*th-d;
float lean=0.5+2.4*bent;
float aa=px*F;
float xw=mod(xs,512.0*cellW)*F;
float dens=0.16+0.50*smoothstep(0.38,0.66,vn(vec2(xs*F*0.021+3.0,wz*5.0)))+0.30*bent;
vec3 bN=vec3(0.0);
vec3 bF=vec3(0.0);
if(j0+2.0>=0.0){
float en=(e1+th)/hb;
float jw=mod(j0+2.0,61.0);
bN=bladeRow(xw+h11(jw*0.73+fi)*57.0-lean*en*en,en,jw*1.31+fi*11.0,aa,j0+2.0<0.5?1.0:dens);
}
if(j0+1.0>=0.0){
float en=e1/hb;
float jw=mod(j0+1.0,61.0);
bF=bladeRow(xw+h11(jw*0.73+fi)*57.0-lean*en*en,en,jw*1.31+fi*11.0,aa,j0+1.0<0.5?1.0:dens);
}
float cN=bN.x;
float cF=bF.x*(1.0-cN);
float gcov=cN+cF;
float tipv=(bN.y*cN+bF.y*cF)/max(gcov,0.001);
float tone=(bN.z*cN+bF.z*cF)/max(gcov,0.001);
cov=max(sat(d/px+0.5),gcov);
float detail=sat((cellW/px-0.7)/1.4)*calm*(0.40+0.20*(fi-3.0));
dab=(tone-0.42+band*0.60)*gcov*(0.4+0.6*tipv)*detail;
if(fi>3.5){
float fcs=hb*1.5;
vec2 fq=vec2(xs,d0+hb*0.5)/fcs;
vec2 fid=floor(fq);
vec2 fid2=vec2(mod(fid.x,512.0),fid.y)+fi*7.0;
vec2 fo=h22(fid2+3.3);
float meadow=smoothstep(0.50,0.72,vn(vec2(wx*1.9+9.0,wz*3.3+fi)));
vec2 fe=(fract(fq)-0.25-0.5*fo)*fcs-vec2(bent*hb*0.3,0.0);
float frad=hb*(0.085+0.07*fo.x);
flw=smoothstep(frad+px*0.8,frad-px*0.8,length(fe))*step(h21(fid2),0.05+0.30*meadow)*step(0.0,d0)*calm;
flwC=mix(vec3(1.0,0.99,0.94),vec3(1.0,0.90,0.50),step(0.62,fo.y));
flwC=mix(flwC,vec3(1.0,0.76,0.84),step(0.88,fo.y));
}
}else{
dab=(vn(vec2(wx*26.0-bent*2.0,wz*44.0))-0.5)*0.14*calm;
}
float light=day4f(0.50,0.52,0.44,0.42)+(lam-0.5)*day4f(0.55,0.75,0.70,0.50)+(0.45-v)*day4f(0.30,0.42,0.46,0.40)+(blot-0.5)*day4f(0.18,0.34,0.20,0.18)-0.08*nr;
float csAmt=day4f(0.05,0.33,0.09,0.07)*(1.0-0.3*uSoft);
float windVis=(1.0-0.35*uSoft)*(1.0+0.3*uLevel)*(1.0-0.25*uWind);
light+=dab*0.55-cs*csAmt+(band*day4f(0.16,0.24,0.34,0.26)+bent*0.05)*windVis;
vec3 col=mix(gSha,gMid,smoothstep(0.08,0.52,light));
col=mix(col,gLit,smoothstep(0.48,0.94,light));
col=mix(col,sheenC,smoothstep(0.86,1.30,light)*0.85);
col=mix(col,sheenC,band*day4f(0.12,0.20,0.30,0.22)*windVis);
vec3 flTint=day4(hx(255.0,236.0,232.0),hx(255.0,255.0,250.0),hx(255.0,214.0,170.0),hx(70.0,130.0,140.0));
col=mix(col,flwC*flTint*(1.0-0.9*cs*csAmt),flw*0.92);
col=mix(col,hazeC,sat(0.80*pow(0.66,fi)*day4f(1.12,1.0,1.0,1.0)));
float mistAmt=day4f(0.85,0.20,0.42,0.44)*(1.0-0.55*nr);
float mist=exp(-max(ryNear-y,0.0)/(0.016+0.02*(1.0-nr)))*mistAmt*(0.55+0.7*fbm3(vec2(wx*0.5+t*0.012,fi*3.0+1.0)));
col=mix(col,hazeC,sat(mist));
return vec4(col,cov);
}
float heap(float x,float sd,float hMax,float off){
float m=(0.5+0.5*sin(x*5.2+2.6))*0.50+(0.5+0.5*sin(x*11.3+sd*2.1))*0.33+(0.5+0.5*sin(x*21.7+sd*5.3))*0.17;
float lim=mix(1.0,0.52,smoothstep(-0.10,0.04,(x-off)*uSize.y/uSize.x));
return hMax*lim*pow(smoothstep(0.30,0.90,m),0.85);
}
vec4 domes(float x,float up,float sd,float hMax,float off){
float cw=hMax*0.90;
float id=floor(x/cw);
float S=0.0;
float mx=0.0;
float R=0.0;
vec2 G=vec2(0.0);
vec2 D=vec2(0.0);
for(int i=0;i<3;i++){
for(int j=0;j<4;j++){
float cid=id+float(i)-1.0;
float fj=float(j);
vec2 r=h22(vec2(mod(cid,97.0),fj*7.0+sd));
float cx=(cid+0.5+(r.x-0.5)*(0.86-0.14*fj+0.56*step(2.5,fj)))*cw;
float E=heap(cx,sd,hMax,off);
float rad=max(E*(0.46-0.085*fj)*(0.85+0.30*r.y),0.004);
float dy=up-E*(0.36+0.15*fj-0.17*fj*step(2.5,fj));
vec2 sc=vec2(mix(0.55,1.0,smoothstep(0.18,0.55,E/hMax)),dy<0.0?0.45:1.0)/rad;
vec2 dv=vec2(x-cx,dy)*sc;
float gs=smoothstep(0.10,0.22,E/hMax)*exp(-1.08*dot(dv,dv));
mx=max(mx,gs);
gs*=gs;
S+=gs;
R+=gs*rad;
G+=gs*dv;
D+=gs*dv*sc;
}
}
float F=sqrt(S);
S=max(S,0.0000001);
return vec4((F-0.34)/max(2.16*F*length(D)/S,3.5),G/S,max(R/S,hMax*0.18)*(0.30+0.70*smoothstep(0.50,0.90,mx*mx/S)));
}
float billow(vec2 q){float n=2.0*vn(q)-1.0;return sqrt(n*n+0.03)-0.17;}
vec2 cumulus(vec2 q,float baseY,float hMax,float sd,vec2 L,float off){
float up=baseY-q.y;
float hp=heap(q.x,sd,hMax,off);
vec2 nq=q/(hMax*0.42)+vec2(sd*3.0,sd*3.0+uTime*0.004);
float n1=vn(nq);
float n2=vn(nq+L*0.45);
vec2 wq=q/(hMax*0.15)+sd*7.0;
vec2 w=(vec2(n1,vn(nq+14.0))-0.5)*hMax*0.12+(vec2(vn(wq),vn(wq+31.0))-0.5)*hMax*0.045;
vec4 dm=domes(q.x+w.x,up-w.y,sd,hMax,off);
float df=hp*0.46-up+w.y;
float d0=0.5*(dm.x+df+sqrt(sq(dm.x-df)+sq(hMax*0.10)));
vec2 bq=rot(0.55)*q/(hMax*0.24)+sd*5.0;
float b1=billow(bq);
float b2=billow(bq*2.6+3.1);
float fz=fbm3(bq*3.1+7.0);
float lump=0.50+0.8*vn(q/hMax*2.3+sd*9.0);
float tail=smoothstep(0.50,0.12,hp/hMax);
float b0=billow(dm.yz/max(length(dm.yz),0.001)*2.3+sd*11.0);
float d=d0+dm.w*(0.30*b0+(0.36*b1+0.06*b2)*lump+fz*(0.05+0.09*tail));
float soft=hMax*mix(0.007,0.13,sat(tail+smoothstep(0.55,0.90,lump/1.30)*0.18+smoothstep(0.45,0.05,b1)*0.10));
float a=smoothstep(-soft,soft,d)*smoothstep(-0.004,0.024,up)*smoothstep(-0.40*hMax,-0.28*hMax,d0);
float relief=clamp(dot(dm.yz,vec2(L.x,-L.y)),-1.0,1.0);
float e=sat(1.0-up/max(hp*1.15,0.0001));
float nearE=smoothstep(hMax*0.45,0.0,d);
float lit=sat(0.54+relief*0.42+(n1-n2)*0.72-e*0.36+((b1-billow(bq+rot(0.55)*L*0.30))*0.46+(b2-billow(bq*2.6+3.1+rot(0.55)*L*0.5))*0.18)*nearE+nearE*0.05);
return vec2(a,lit);
}
vec4 treeAt(vec2 q,float R,float g,float t,vec3 lit,vec3 mid,vec3 sha,vec3 rimC,vec2 L,float px,float bias){
vec2 u=q/R;
float aa=px/R;
float hgt=sat(u.y/2.2);
u.x-=(0.03+0.14*g)*hgt*hgt+0.010*sin(t*1.7+u.y*3.0)*hgt;
float tx=0.06*sin(u.y*1.6+0.4)-0.02;
float tw=mix(0.100,0.050,sat(u.y/1.0))+0.07*exp(-max(u.y,0.0)/0.09);
float ts=aa*1.6;
float trunk=smoothstep(-ts,ts,tw-abs(u.x-tx))*smoothstep(-0.07,-0.02,u.y)*step(u.y,1.25);
float bl=u.y-0.55;
float br1=smoothstep(-ts,ts,0.040-bl*0.02-abs(u.x-tx+bl*0.95))*step(0.0,bl)*step(bl,0.6);
float br2=smoothstep(-ts,ts,0.040-bl*0.02-abs(u.x-tx-bl*1.10))*step(0.0,bl)*step(bl,0.6);
trunk=max(trunk,max(br1,br2));
vec2 fl=vec2(sin(t*0.23)+0.5*sin(t*0.61+1.3),cos(t*0.19)+0.5*sin(t*0.53+2.1))*0.10+(g-0.4)*vec2(0.25,0.0);
float S=0.0001;
float Wt=0.0001;
vec2 G=vec2(0.0);
for(int i=0;i<9;i++){
vec2 cc=vec2(0.06,1.22);
float rr=0.58;
if(i==1){cc=vec2(-0.84,1.00);rr=0.36;}
if(i==2){cc=vec2(1.02,1.02);rr=0.38;}
if(i==3){cc=vec2(-0.50,1.50);rr=0.46;}
if(i==4){cc=vec2(0.60,1.52);rr=0.46;}
if(i==5){cc=vec2(-0.30,0.86);rr=0.30;}
if(i==6){cc=vec2(1.36,0.84);rr=0.19;}
if(i==7){cc=vec2(0.06,1.86);rr=0.38;}
if(i==8){cc=vec2(0.52,0.78);rr=0.24;}
vec2 dn=(u-cc)/rr;
float gs=exp(-dot(dn,dn)*1.386);
S+=gs;
gs*=gs;
Wt+=gs;
G+=gs*dn;
}
G/=Wt;
float F=sqrt(S);
vec2 lq=rot(0.6)*u*8.0+fl*1.5+5.0;
float lf=billow(lq)*0.60+billow(lq*2.3+3.7)*0.40;
float n1=fbm3(rot(-0.4)*u*2.1+fl+11.0);
float edge=smoothstep(0.95,0.50,F)*smoothstep(0.24,0.40,F);
float Fn=F+edge*((lf-0.28)*0.42+(n1-0.5)*0.30);
float crown=smoothstep(0.50-0.045-aa,0.50+0.045+aa,Fn);
float relief=clamp(dot(G,L),-1.0,1.0);
float glob=dot(u-vec2(0.15,1.25),L);
float light=0.50+bias+relief*0.34+glob*0.17+(n1-0.5)*0.26+(lf-0.35)*0.12;
vec3 cc3=mix(sha,mid,smoothstep(0.08,0.56,light));
cc3=mix(cc3,lit,smoothstep(0.44,0.98,light));
cc3=mix(cc3,rimC,smoothstep(0.85,0.50,Fn)*sat(0.25+relief)*0.40);
float al=crown+trunk*(1.0-crown);
vec3 tc=mix(sha*0.50+vec3(0.025,0.018,0.02),mid*0.62,sat(0.5+(u.x-tx)/tw*L.x*0.9)*0.55);
vec3 col=mix(tc,cc3,crown/max(al,0.0001));
return vec4(col,al);
}
float seedLayer(vec2 p,float cs,float speed,float flowK,float rad,float sd,float t,float dens,float yTop,float kind,float px){
float span=cs*64.0;
vec2 q=vec2(p.x+mod(uFlow*flowK-t*speed,span)+span,p.y+3.0)/cs;
vec2 id=floor(q);
vec2 idw=vec2(mod(id.x,64.0),id.y);
float r=h21(idw+sd);
vec2 o=h22(idw+sd+7.7);
float ph=r*6.2832;
vec2 wob=vec2(sin(t*(0.23+0.2*o.x)+ph),sin(t*(0.31+0.25*o.y)+ph*1.7))*0.17;
vec2 e=(fract(q)-0.5-(o-0.5)*0.44-wob)*cs;
float dist=length(e);
float sz0=rad*(0.6+0.8*o.x);
float sz=max(sz0,px*0.75);
float v=(glow(dist,sz)+0.22*glow(dist,sz*2.8))*sq(sz0/sz);
if(kind>1.5){
vec2 e2=rot(0.45*sin(t*0.5+ph)+0.5)*e;
float ang=atan(e2.x,-e2.y+0.000001);
float fan=smoothstep(2.3,1.8,abs(ang));
float ray=smoothstep(px*1.5,px*0.2,abs(sin(ang*7.0))*dist)*fan*smoothstep(sz,sz*0.55,dist);
float stalk=smoothstep(px*1.4,px*0.2,abs(e2.x))*step(0.0,e2.y)*smoothstep(sz*1.5,sz*1.25,e2.y);
v=ray*0.62+stalk*0.55+0.30*glow(dist,sz*0.8)*fan+glow(length(e2-vec2(0.0,sz*1.45)),max(px*1.2,sz*0.10));
}else if(kind>0.5){
v=smoothstep(sz,sz*0.72,dist)*(0.55+0.45*smoothstep(sz*0.40,sz*0.95,dist));
}
return step(r,dens)*smoothstep(yTop,yTop+0.16,p.y)*v*(0.45+0.55*o.y);
}
vec4 foreGrass(vec2 p,float asp,float g,float t,vec3 cDark,vec3 cLite,vec3 flTint,float lx){
float flowX=uFlow*0.95;
float cw=0.090;
float ci=floor((p.x+flowX)/cw);
vec3 acc=vec3(0.0);
float aAcc=0.0;
for(int k=0;k<6;k++){
float id=ci+float(k)-4.0;
float idw=mod(id,256.0);
float ra=h21(vec2(idw,1.3));
float rb=fract(ra*13.7+0.21);
float tx=(id+0.2+0.6*ra)*cw-flowX;
float side=smoothstep(0.12,0.44,abs(tx/asp-0.5));
float yb=1.02+(1.0-side)*0.46;
float tall=(0.70+0.55*rb)*(0.75+0.25*side);
for(int j=0;j<7;j++){
float fj=float(j);
float r1=h21(vec2(idw+fj*13.1,7.7));
float r2=fract(r1*7.13+0.31);
float r3=fract(r1*17.71+0.57);
float r4=fract(r1*31.37+0.11);
float isFl=step(4.5,fj)*step(rb+0.25*(6.0-fj),0.70)*smoothstep(0.45,0.85,side);
float rx=tx+(r4-0.5)*0.08;
float H=min(mix(0.11+0.44*r2*r2,0.17+0.20*r2,isFl)*tall,0.50);
float l0=(fj-3.0)*0.13+(r3-0.5)*0.30;
float bend=0.04+0.55*g*(0.75+0.5*r4)+(r3-0.3)*0.25+0.03*sin(t*(1.2+r4)+r3*20.0);
float Hs=H/sqrt(1.0+0.8*sq(l0+bend));
float s=(yb-p.y)/Hs;
float sc=clamp(s,0.0,1.0);
float cx=rx+Hs*(l0*sc+bend*sc*sc);
float slope=l0+2.0*bend*sc;
float dperp=abs(p.x-cx)/sqrt(1.0+slope*slope);
float w=(0.0045+0.0060*r4)*(0.7+0.7*H/0.5)*pow(1.0-sc,0.9)*(0.65+0.35*smoothstep(0.0,0.2,sc));
w=mix(w,0.0022,isFl);
float B=0.0045+0.005*r2*r2;
float lite=step(0.74,r3);
float a=sat((min(dperp+w,B)-max(dperp-w,-B))/(2.0*B))*step(0.0,s)*smoothstep(1.0,0.92,s)*mix(0.95,0.80,lite);
vec3 bc=mix(cDark,cLite,sat(0.05+0.60*lite+0.75*sc*sc*(0.4+0.9*r3)+0.40*sat((p.x-cx)/(w+B)*lx)*smoothstep(0.05,0.45,sc)));
acc=acc*(1.0-a)+bc*a;
aAcc=aAcc*(1.0-a)+a;
if(isFl>0.01){
vec2 dv=p-vec2(rx+Hs*(l0+bend),yb-Hs);
float fr=0.016+0.012*r2;
float rad=fr*(0.80+0.20*cos(atan(dv.y,dv.x+0.00001)*8.0+r3*6.0+bend*2.0));
float dl=length(dv);
float fa=smoothstep(rad+0.004+0.008*r2,rad-0.004-0.008*r2,dl)*0.94*isFl;
vec3 fc=mix(hx(255.0,252.0,246.0),hx(255.0,214.0,224.0),step(0.70,r3));
fc=mix(fc,hx(255.0,214.0,110.0),smoothstep(fr*0.50,fr*0.15,dl))*flTint;
acc=acc*(1.0-fa)+fc*fa;
aAcc=aAcc*(1.0-fa)+fa;
}
}
}
float edge=smoothstep(0.24,0.46,abs(p.x/asp-0.5));
float under=smoothstep(0.0,0.07,p.y-(1.0-0.105*edge*(0.55+0.9*vn(vec2((p.x+flowX)*22.0,3.0)))))*edge*0.92;
acc=acc*(1.0-under)+cDark*0.92*under;
aAcc=aAcc*(1.0-under)+under;
return vec4(acc,aAcc);
}
vec4 sceneMain(vec2 xy){
vec2 uv=xy/uSize;
float asp=uSize.x/uSize.y;
vec2 p=vec2(uv.x*asp,uv.y);
float c=p.x-0.5*asp;
float px=1.0/uSize.y;
float t=uTime;
float calm=1.0-0.38*uSoft;
float nt=day4f(0.03,0.0,0.06,1.0);
float g=gust(p.x,t);
float sdv=daySeed();
vec3 skyTop=day4(hx(88.0,120.0,156.0),hx(46.0,116.0,170.0),hx(60.0,66.0,134.0),hx(4.0,13.0,30.0));
vec3 skyMid=day4(hx(142.0,140.0,174.0),hx(84.0,162.0,194.0),hx(190.0,112.0,154.0),hx(8.0,36.0,56.0));
vec3 skyLow=day4(hx(255.0,208.0,198.0),hx(186.0,232.0,218.0),hx(255.0,196.0,136.0),hx(24.0,80.0,94.0));
float sy=sat((HOR-p.y)/HOR);
vec3 col=mix(skyLow,skyMid,smoothstep(0.0,0.19,sy));
col=mix(col,skyTop,smoothstep(0.16,1.0,sy));
float sunAmt=day4f(1.0,0.0,1.0,0.0);
vec2 sunP=vec2(day4f(-0.26,-0.22,0.26,0.26)*asp,HOR-day4f(0.010,0.03,0.014,0.03));
vec3 sunC=day4(hx(255.0,206.0,196.0),hx(255.0,250.0,230.0),hx(255.0,200.0,124.0),hx(255.0,196.0,120.0));
vec2 sv=vec2(c,p.y)-sunP;
float sd=length(sv*vec2(0.6,1.0));
col+=sunC*(0.13*exp(-sd*4.2)+0.19*exp(-sd*11.0))*sunAmt*day4f(0.55,0.0,1.0,0.0)*(1.0+0.4*uLevel);
col=mix(col,vec3(1.0,0.97,0.88),smoothstep(0.029,0.023,length(sv))*sunAmt);
col+=vec3(1.0,1.0,0.92)*exp(-sy*14.0)*day4f(0.05,0.10,0.04,0.0)*(1.0+0.4*uLevel);
if(nt>0.02){
float st=stars(p+vec2(uFlow*0.003,0.0),26.0)+stars(p*1.7+3.3,34.0)*0.6;
float mw=exp(-sq((p.y-0.20+c*0.26)/0.12))*fbm3(vec2(c*1.7+p.y*2.6+sdv*9.0,p.y*3.4-c*1.1));
col+=(vec3(0.80,0.96,1.0)*st*(0.80+1.2*mw)*smoothstep(0.56,0.08,p.y)+vec3(0.16,0.34,0.40)*mw*mw*0.55)*nt;
vec2 mv=vec2(c,p.y)-vec2(0.385*asp,0.385);
float md=length(mv);
float moon=smoothstep(0.0225,0.0205,md)*smoothstep(0.0180,0.0205,length(mv-vec2(0.0095,-0.0050)));
col+=vec3(0.92,1.0,0.94)*(moon*0.95+0.10*exp(-md*30.0)+0.05*exp(-md*8.0))*nt;
}
vec3 cLit=day4(hx(255.0,232.0,224.0),hx(255.0,255.0,250.0),hx(255.0,212.0,170.0),hx(70.0,116.0,128.0));
vec3 cSha=day4(hx(170.0,168.0,196.0),hx(146.0,190.0,218.0),hx(168.0,108.0,152.0),hx(12.0,38.0,58.0));
vec2 Ls=normalize(vec2(day4f(-0.8,-0.5,0.8,0.3),-0.75));
if(p.y<HOR+0.04){
float drift=t*(0.003+0.006*uWind);
float cir=fbm3(vec2(c*1.1-drift*1.6+sdv*9.0,p.y*6.5-c*0.9));
col=mix(col,cLit,smoothstep(0.48,0.86,cir)*smoothstep(0.02,0.22,p.y)*smoothstep(0.52,0.30,p.y)*0.075*calm);
if(p.y>0.33){
float off=sdv*1.9-drift+uFlow*0.014;
vec2 cl=cumulus(vec2(c+off,p.y),HOR+0.050,0.195,2.0+floor(h11(sdv*37.0)*8.0),Ls,off);
vec3 kc=mix(mix(cSha,cLit,cl.y),skyLow,0.12);
col=mix(col,kc,cl.x*mix(0.72,0.97,smoothstep(0.45,0.52,p.y)));
col+=sunC*cl.x*(1.0-cl.x)*exp(-sd*4.5)*0.28*sunAmt;
}
}
if(p.y>HOR-0.12){
float fa=-1.0;
float fb=-1.0;
float rya=2.0;
float ryb=2.0;
float fullA=0.0;
for(int i=0;i<6;i++){
float fi=float(i);
float ry=ridgeY(c,fi);
if(p.y>ry-bladeLen(fi)*1.3){
if(fullA>0.5){fb=fa;ryb=rya;}
fa=fi;
rya=ry;
fullA=step(ry+bladeLen(fi),p.y);
}
}
float fringe=1.0-fullA;
if(fb>-0.5&&fringe>0.5){
vec4 hb=hillShade(c,p.y,fb,ryb,ridgeY(c,fb+1.0),t,px,calm);
col=mix(col,hb.rgb,hb.a);
}
vec4 tr=vec4(0.0);
float dx=treeDx(c);
if(abs(dx)<0.20&&(fa<3.5||fringe>0.5)){
vec2 q=vec2(dx,ridgeY(c-dx,3.0)+0.003-p.y);
if(q.y<0.20&&q.y>-0.03){
vec3 tLit=day4(hx(170.0,204.0,156.0),hx(160.0,214.0,104.0),hx(250.0,190.0,96.0),hx(34.0,92.0,92.0));
vec3 tMid=day4(hx(100.0,150.0,128.0),hx(56.0,146.0,90.0),hx(110.0,98.0,74.0),hx(10.0,42.0,50.0));
vec3 tSha=day4(hx(64.0,104.0,108.0),hx(18.0,82.0,84.0),hx(50.0,48.0,68.0),hx(4.0,20.0,28.0));
vec3 tRim=day4(hx(255.0,226.0,210.0),hx(214.0,240.0,150.0),hx(255.0,216.0,130.0),hx(110.0,180.0,170.0));
vec3 hazeT=day4(hx(240.0,220.0,220.0),hx(172.0,224.0,216.0),hx(244.0,172.0,152.0),hx(24.0,74.0,90.0));
tr=treeAt(q,TREE_R,g,t,tLit,tMid,tSha,tRim,Ls*vec2(1.0,-1.0),px,day4f(-0.06,0.0,-0.20,-0.10));
tr.rgb=mix(tr.rgb,hazeT,0.16);
float shd=exp(-sq((q.x+0.5*TREE_R*Ls.x)/(TREE_R*1.3))-sq((q.y+0.004)/0.007))*day4f(0.5,1.0,0.7,0.7)*step(q.y,0.004);
if(tr.a<0.001)tr.a=-shd;
}
}
if(fa>-0.5){
vec4 ha=hillShade(c,p.y,fa,rya,fa<4.5?ridgeY(c,fa+1.0):1.06,t,px,calm);
if(fa>3.5)col=mix(col,tr.rgb,max(tr.a,0.0));
col=mix(col,ha.rgb,ha.a);
if(fa<3.5){
col=mix(col,col*vec3(0.55,0.78,0.80),-min(tr.a,0.0)*step(2.5,fa));
col=mix(col,tr.rgb,max(tr.a,0.0));
}
}else{
col=mix(col,tr.rgb,max(tr.a,0.0));
}
}
vec3 seedC=day4(hx(255.0,244.0,236.0),hx(255.0,255.0,240.0),hx(255.0,230.0,180.0),hx(150.0,210.0,200.0));
float seedAmt=day4f(0.70,0.75,0.90,0.30)*(1.0-0.5*uSoft);
float sFar=seedLayer(p+vec2(-0.03,-0.012)*(g-0.45),0.105,0.030,0.25,0.00108,3.0,t,0.45,0.60,0.0,px);
float sMid=seedLayer(p+vec2(-0.06,-0.022)*(g-0.45),0.190,0.055,0.50,0.00215,9.0,t,0.50,0.53,0.0,px);
float sHero=seedLayer(p+vec2(-0.08,-0.028)*(g-0.45),0.360,0.050,0.70,0.0124,23.0,t,0.55,0.50,2.0,px);
col+=seedC*(sFar*0.40+sMid*0.75+sHero*0.80)*seedAmt;
if(nt>0.02){
for(int i=0;i<16;i++){
float fi=float(i);
float hx0=h11(fi*3.1+sdv*7.0);
float hy0=h11(fi*5.7+1.0);
vec2 fp=vec2(mod(hx0*(asp+0.3)-uFlow*0.35,asp+0.3)-0.15,0.65+0.32*hy0);
fp+=vec2(sin(t*0.21+fi*2.3)+0.5*sin(t*0.47+fi*4.1),cos(t*0.17+fi*1.7)+0.5*sin(t*0.39+fi*3.3))*0.030;
float rr=max(0.0018+0.0029*hx0,px*1.2);
float dd=length(p-fp);
col+=hx(214.0,255.0,130.0)*(glow(dd,rr)+0.34*glow(dd,rr*4.5)+0.10*glow(dd,rr*14.0))*pow(0.5+0.5*sin(t*(0.55+0.4*hy0)+fi*2.9),3.0)*nt*(1.0-0.3*uSoft)*(1.0+0.5*uLevel);
}
}
float rang=atan(sv.x,-sv.y+0.00001);
float rays=smoothstep(0.45,0.90,vn(vec2(rang*5.0+t*0.010,1.0))*0.60+vn(vec2(rang*11.0-t*0.014,5.0))*0.40);
float rd=length(sv);
col+=sunC*rays*day4f(0.030,0.0,0.050,0.0)*exp(-rd*2.6)*smoothstep(0.05,0.30,rd)*smoothstep(0.0,0.16,-sv.y)*(1.0-0.3*uSoft);
if(p.y>0.48){
vec3 fDark=day4(hx(54.0,96.0,100.0),hx(20.0,96.0,78.0),hx(40.0,38.0,56.0),hx(3.0,14.0,20.0));
vec3 fLite=day4(hx(150.0,190.0,158.0),hx(122.0,206.0,110.0),hx(232.0,160.0,92.0),hx(16.0,58.0,62.0));
vec3 flTint=day4(hx(255.0,232.0,232.0),hx(255.0,255.0,255.0),hx(255.0,206.0,170.0),hx(60.0,100.0,120.0));
vec4 fg=foreGrass(p,asp,g,t,fDark,fLite,flTint,day4f(-1.0,-1.0,1.0,1.0));
float fk=1.0-0.3*uSoft;
col=col*(1.0-fg.a*fk)+fg.rgb*fk;
}
float sNear=seedLayer(p+vec2(-0.10,-0.035)*(g-0.45),0.46,0.110,0.95,0.016,17.0,t,0.45,0.53,1.0,px);
col+=seedC*sat(sNear)*0.20*seedAmt;
col+=skyLow*exp(-sq((p.y-HOR)/0.16))*0.045*uLevel;
col+=sunC*0.12*exp(-sd*2.6)*sunAmt*day4f(0.6,0.0,1.0,0.0);
return finish(col,xy);
}
uniform float uDim;
uniform vec4 uVeil;
uniform float uFade;
void main() {
    vec4 c = sceneMain(vec2(gl_FragCoord.x, uSize.y - gl_FragCoord.y));
    gl_FragColor = vec4(mix(c.rgb, uVeil.rgb, uVeil.a) * (1.0 - uDim), uFade);
}
