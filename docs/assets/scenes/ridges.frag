// ridges — tools/site/agsl2glsl.mjs 가 app/src/main/assets/scenes/_lib.agsl + ridges.agsl 에서 만든 파일. 손으로 고치지 말 것.
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
vec3 sat3(vec3 v){return clamp(v,vec3(0.0),vec3(1.0));}
vec3 scr(vec3 a,vec3 b){return 1.0-(1.0-a)*(1.0-sat3(b));}
float hl(float i,float s){return h21(vec2(mod(i,400.0),s));}
float vnp(vec2 p){
vec2 i=floor(p);
vec2 f=p-i;
vec2 u=f*f*(3.0-2.0*f);
vec2 i0=mod(i,400.0);
vec2 i1=mod(i+1.0,400.0);
return mix(mix(h21(i0),h21(vec2(i1.x,i0.y)),u.x),mix(h21(vec2(i0.x,i1.y)),h21(i1),u.x),u.y);
}
float fbmp(vec2 p){
float v=0.0;
float a=0.5;
for(int k=0;k<5;k++){v+=a*vnp(p);p=p*2.0+vec2(13.37,7.61);a*=0.5;}
return v;
}
float gustp(float x,float t){
float g=vnp(vec2(x*0.9-mod(t*0.35,400.0),mod(t*0.07,400.0)))*0.65+vnp(vec2(x*2.3-mod(t*0.8,400.0),3.7))*0.35;
return sat(g*(0.55+0.9*uWind));
}
float g1(float x,float s){
float i=floor(x);
float f=x-i;
float u=f*f*(3.0-2.0*f);
return mix((hl(i,s)*2.0-1.0)*f,(hl(i+1.0,s)*2.0-1.0)*(f-1.0),u)*2.0;
}
float peaks(float x,float s){
float i=floor(x);
float m=0.0;
for(int k=0;k<3;k++){
float id=i+float(k)-1.0;
float r=hl(id,s);
float d=x-(id+0.15+0.70*r);
float w=0.26+0.60*(d<0.0?fract(r*157.31):fract(r*613.7));
float e=1.35+0.60*fract(r*977.1);
float ad=abs(d);
m=max(m,exp(-pow(ad/w+0.0001,e))*(0.42+0.58*fract(r*41.37))*smoothstep(1.15,0.80,ad));
}
return m;
}
vec2 ridge(float x,float s){
float w=x+0.34*g1(x*0.55+4.0,s+1.0)+0.06*g1(x*2.3+1.0,s+3.0);
float a=peaks(w,s);
float b=peaks(w*2.1+7.7,s+11.0)*0.50+0.08;
float c=peaks(w*4.9+3.3,s+31.0)*0.16+0.04;
float base=(0.5+0.5*g1(w*0.37+2.2,s+23.0))*0.40+(0.60*g1(w*3.4,s+5.0)+0.34*g1(w*7.3+3.1,s+12.0))*0.050;
float m=max(a,b);
return vec2(max(m,c+m*0.52),max(b,c+b*0.52))*0.60+base;
}
float canopy(float u,float s){
float v=u+0.45*g1(u*0.61,s+2.0);
float i1=floor(v);
float f1=v-i1;
float r1=hl(i1,s);
float t1=sat(1.0-abs(f1-0.5-(fract(r1*13.3)-0.5)*0.44)/(0.20+0.30*fract(r1*31.7)));
float c1=mix(t1,t1*(2.0-t1),fract(r1*7.9))*(0.12+0.88*r1*r1);
float v2=v*2.37+0.37;
float i2=floor(v2);
float f2=v2-i2;
float r2=hl(i2,s+5.0);
float t2=1.0-abs(2.0*f2-1.0);
float c2=t2*(2.0-t2)*(0.10+0.40*r2);
float big=0.5+0.5*g1(u*0.17+1.3,s+9.0);
return max(c1,c2)*(0.35+0.65*big)+0.40*big;
}
float segd(vec2 q,vec2 a,vec2 b){
vec2 qa=q-a;
vec2 ba=b-a;
float k=sat(dot(qa,ba)/dot(ba,ba));
return length(qa-ba*k);
}
float limb(vec2 q,vec2 a,vec2 b,float ra,float rb){
vec2 qa=q-a;
vec2 ba=b-a;
float k=sat(dot(qa,ba)/dot(ba,ba));
return mix(ra,rb,k)-length(qa-ba*k);
}
float trunkX(float h,float r,float sway){
return(r-0.5)*0.40*h+0.085*sin(h*3.3+r*20.0)*h+0.016*sin(h*9.0+r*50.0)+sway*h*h;
}
float pine(vec2 q,float r,float n,float m,float sway,float tt){
float h=q.y;
float tw=mix(0.040,0.009,sat(h*1.03));
float d=min(tw-abs(q.x-trunkX(h,r,sway)),0.97-h);
float flip=r>0.5?1.0:-1.0;
float rough=(n-0.5)*0.034+(m-0.5)*0.070;
for(int k=0;k<7;k++){
float fk=float(k);
float u=max(fk-1.0,0.0)/5.0;
float rk=fract(r*17.3+fk*0.618);
float rk2=fract(r*71.7+fk*0.377);
float rk3=fract(r*33.1+fk*0.845);
float side=(mod(fk,2.0)<0.5?-1.0:1.0)*flip;
float hk=0.66+0.34*u+(rk-0.5)*0.05;
float off=side*(0.05+0.21*rk2)*(1.0-0.78*u);
float rx=(0.095+0.085*rk3)*(1.0-0.28*u);
if(k==0){
hk=0.55+0.08*rk;
off=-flip*(0.25+0.10*rk2);
rx=0.115+0.03*rk3;
}
vec2 c=vec2(trunkX(hk,r,sway)+off,hk+0.005*sin(tt*1.7+fk*2.4+r*9.0));
vec2 rad=vec2(rx,rx*(0.30+0.10*rk));
vec2 e=(q-c)/rad;
e.y+=e.x*(rk3-0.45)*0.45;
if(e.y<0.0)e.y*=1.7;
float lobe=max(1.0-length(e),0.72-length(e-vec2(side*0.62,0.42))*1.25);
lobe=max(lobe,0.60-length(e-vec2(-side*0.55,0.30+0.3*rk))*1.5)+0.07*sin(e.x*6.0+rk*30.0)*sat(e.y+0.3);
d=max(d,lobe*rad.y+rough*(0.55+0.45*sat(e.y+0.5)));
float hb=hk-0.055-0.40*abs(off);
vec2 b0=vec2(trunkX(hb,r,sway),hb);
vec2 b2=c-vec2(0.0,rad.y*0.25);
vec2 b1=mix(b0,b2,0.55)-vec2(0.0,0.07*abs(off));
float rb=0.0066-0.0018*u;
d=max(d,max(limb(q,b0,b1,rb*1.7,rb),limb(q,b1,b2,rb,0.0036)));
}
return d;
}
float bird(vec2 q,float f){
q.x=abs(q.x);
vec2 e=vec2(0.48,0.30*f+0.12);
vec2 w=vec2(1.0,0.52*f-0.06);
float d=max(0.085-segd(q,vec2(0.0),e),0.055-segd(q,e,w));
return max(d,0.13-length(q*vec2(0.75,1.5)));
}
vec4 sceneMain(vec2 xy){
vec2 uv=xy/uSize;
float asp=uSize.x/uSize.y;
vec2 p=vec2(uv.x*asp,uv.y);
float px=1.0/uSize.y;
float t=uTime;
float cx=p.x-0.5*asp;
float calm=1.0-0.38*uSoft;
float seedOff=fract(uSeed*0.61803+0.32621)*37.0;
float gu=gustp(cx*1.2,t);
float nt=day4f(0.0,0.0,0.0,1.0);
float dayAmt=day4f(0.0,1.0,0.0,0.0);
vec3 skyTop=day4(hx(104.0,112.0,176.0),hx(46.0,132.0,166.0),hx(56.0,44.0,108.0),hx(7.0,11.0,36.0));
vec3 skyMid=day4(hx(160.0,120.0,154.0),hx(84.0,160.0,178.0),hx(142.0,80.0,134.0),hx(18.0,28.0,72.0));
vec3 skyLow=day4(hx(248.0,202.0,172.0),hx(198.0,232.0,224.0),hx(255.0,178.0,102.0),hx(44.0,64.0,124.0));
vec3 sunCol=day4(hx(255.0,228.0,200.0),hx(255.0,255.0,244.0),hx(255.0,208.0,144.0),hx(214.0,226.0,255.0));
vec3 rFar=day4(hx(212.0,168.0,182.0),hx(150.0,206.0,202.0),hx(206.0,128.0,118.0),hx(46.0,66.0,122.0));
vec3 rMid=day4(hx(108.0,104.0,160.0),hx(48.0,124.0,130.0),hx(98.0,62.0,116.0),hx(20.0,30.0,70.0));
vec3 rNear=day4(hx(30.0,32.0,78.0),hx(10.0,54.0,64.0),hx(24.0,16.0,52.0),hx(5.0,8.0,24.0));
vec3 fogLit=day4(hx(255.0,222.0,200.0),hx(246.0,252.0,242.0),hx(255.0,206.0,140.0),hx(142.0,168.0,226.0));
vec3 fogShade=day4(hx(200.0,172.0,206.0),hx(150.0,206.0,206.0),hx(150.0,96.0,150.0),hx(28.0,42.0,90.0));
float sy=sat(p.y/0.60);
vec3 col=mix(skyTop,skyMid,smoothstep(0.0,0.70,sy));
col=mix(col,skyLow,pow(max(smoothstep(0.60,1.0,sy),0.0001),2.6));
float sunAmt=day4f(1.0,0.0,1.0,0.0)*(1.0-0.5*uRain);
float sunX=day4f(0.14,0.26,-0.16,-0.16)*asp;
float farCrest=0.622-0.1147*ridge((mix(sunX,0.17*asp,nt)+uFlow*0.015)*2.2+seedOff,1.7).x;
vec2 sunP=vec2(sunX,day4f(farCrest-0.006,0.42,farCrest-0.006,0.70));
vec2 moonP=vec2(0.17*asp,mix(0.66,farCrest-0.016,nt));
vec2 lightP=mix(sunP,moonP,nt);
float lightAmt=max(sunAmt,nt);
vec2 lv=vec2(cx,p.y)-lightP;
float ld=length(lv);
float ldw=length(lv*vec2(0.42,1.9));
float breath=1.0+0.36*uLevel;
float rays=0.0;
if(sunAmt>0.01){
vec2 sv=vec2(cx,p.y)-sunP;
float rd=max(length(sv),0.0001);
vec2 rn=sv/rd;
float rayN=vnp(rn*2.6+vec2(mod(t*0.011,400.0),seedOff))*0.68+vnp(rn*6.5+vec2(400.0-mod(t*0.017,400.0),3.0+seedOff))*0.32;
float upw=max(-rn.y,0.0);
rays=smoothstep(0.42,0.78,rayN)*exp(-rd*3.4)*smoothstep(0.04,0.16,rd)*(1.0-0.72*upw*upw)*sunAmt;
}
float shafts=0.0;
if(dayAmt>0.01){
vec2 dv=vec2(cx,p.y)-vec2(0.62*asp,-0.55);
vec2 dn=dv/length(dv);
float shN=vnp(dn*8.3+vec2(mod(t*0.011,400.0),seedOff))*0.68+vnp(dn*20.8+vec2(400.0-mod(t*0.017,400.0),3.0+seedOff))*0.32;
shafts=smoothstep(0.42,0.78,shN)*smoothstep(0.50,0.64,p.y)*smoothstep(1.00,0.80,p.y)*dayAmt;
}
if(nt>0.01){
float up=smoothstep(0.56,0.10,p.y);
float st=stars(p+vec2(uFlow*0.003,0.0),26.0)+stars(p*1.7+3.3,34.0)*0.6;
col+=vec3(0.86,0.90,1.0)*st*up*nt*0.85*calm;
}
float vAmt=day4f(1.0,0.0,0.8,0.0);
if(vAmt>0.01){
float vd=length(vec2(cx,p.y)-vec2(day4f(-0.12,0.0,0.20,0.20)*asp,0.205));
col+=vec3(1.0,0.96,0.90)*(glow(vd,px*1.8)+0.22*glow(vd,px*6.0))*vAmt*(0.80+0.20*sin(t*1.3))*calm;
}
if(p.y>0.06&&p.y<0.62){
vec2 cq=vec2(mod(cx*0.80-t*0.0045+uFlow*0.006+seedOff,400.0),p.y*8.5);
float cn=fbmp(cq+vec2(0.0,0.9*vnp(vec2(cq.x,cq.y*0.6)+3.0)));
float cm=smoothstep(0.52,0.82,cn)*smoothstep(0.08,0.30,p.y)*smoothstep(0.60,0.42,p.y);
vec3 cloudC=mix(skyMid*1.10,mix(skyLow,sunCol,0.45),sat((p.y-0.20)*2.8));
col=mix(col,cloudC,cm*day4f(0.50,0.36,0.52,0.32)*calm);
}
float sunFade=1.0-day4f(1.0,0.0,1.0,0.0);
float sunDisc=smoothstep(0.038+0.030*sunFade,0.027-0.008*uSoft-0.020*sunFade,ld)*(1.0-0.6*sunFade);
vec3 gl=sunCol*((0.17*exp(-ldw*3.0)+0.30*exp(-ld*10.0))*sunAmt+0.10*rays*calm)*breath;
col=scr(col,gl);
col=mix(col,mix(sunCol,vec3(1.0),0.80-0.45*smoothstep(-0.030,0.035,lv.y)),sunDisc*sunAmt);
if(nt>0.01){
float disc=smoothstep(0.0275,0.0250,ld);
float mare=0.80+0.20*smoothstep(0.30,0.70,fbm3(lv*38.0+7.0));
col=scr(col,hx(170.0,196.0,255.0)*(0.36*exp(-ld*11.0)+0.17*exp(-ldw*3.2))*breath*nt);
col=mix(col,hx(255.0,247.0,226.0)*mare,disc*nt);
}
float bT=120.0;
float bc=floor(t/bT);
float bu=t/bT-bc;
float bs=mod(bc,97.0);
if(h11(bs*1.37+0.5)>0.28&&bu<0.37){
float bdir=h11(bs*2.11+4.0)>0.5?1.0:-1.0;
float prog=bu/0.37;
vec2 fc0=vec2(bdir*(prog-0.5)*(asp+0.5),0.47-0.11*h11(bs*3.3+1.0)-0.035*prog+0.012*sin(prog*5.0+bs));
vec2 fd=vec2(cx,p.y)-fc0;
if(abs(fd.x)<0.14&&abs(fd.y)<0.07){
float ba=0.0;
for(int j=0;j<7;j++){
float fj=float(j);
float rank=floor((fj+1.0)*0.5);
float sd=mod(fj,2.0)<0.5?-1.0:1.0;
float rj=h11(fj*5.3+bs*7.7+2.0);
vec2 bp=vec2(-bdir*rank*0.030*(0.9+0.3*rj),sd*rank*0.011+(rj-0.5)*0.012);
bp+=0.004*vec2(sin(t*0.31+fj*1.7),sin(t*0.43+fj*2.9));
float sz=0.0078*(0.80+0.40*fract(rj*7.3));
float glide=smoothstep(0.62,0.80,vnp(vec2(mod(t*0.16,400.0)+fj*7.0,fj+bs)));
float flap=mix(sin(6.2832*fract(t*(1.25+0.35*rj)+rj*3.0)),0.35,glide);
vec2 bq=(fd-bp)/sz;
bq.y=-bq.y;
ba=max(ba,sat(bird(bq,flap)*sz/px+0.5)*sat(sz*0.085/px*2.2));
}
col=mix(col,mix(rNear,skyMid,0.30),ba*(0.78-0.45*nt)*(1.0-0.30*uSoft));
}
}
float lightProx=exp(-abs(cx-lightP.x)*1.3);
float lightCol=exp(-abs(cx-lightP.x)*5.0)*lightAmt;
for(int i=0;i<7;i++){
float fi=float(i);
float z=fi/6.0;
float zp=max(z,0.0001);
float baseY=mix(0.622,1.020,pow(zp,1.45));
float amp=mix(0.074,0.230,z)*(i==0?1.55:1.0);
float H=0.060+0.050*z;
float lvl0=baseY-amp*0.52;
float fogTop=lvl0-0.03-0.05*z-1.5*H;
if(p.y>min(baseY-amp*1.05-0.04,i>0?fogTop:9.0)){
float frq=mix(4.0,1.25,sqrt(z))*(i==0?0.55:1.0);
float spd=0.015+0.45*pow(zp,2.2);
float X=cx+uFlow*spd;
vec2 hh=ridge(X*frq+fi*3.17+seedOff,fi*7.31+1.7);
float smoothCrest=baseY-amp*hh.x;
float foldCrest=baseY-amp*hh.y;
float tk=smoothstep(0.15,1.0,z);
float tf=mix(420.0,64.0,tk);
float tree=canopy(X*tf-(0.10+0.30*uWind)*gu*tk,fi+0.5)*mix(0.0020,0.0230,tk*tk)*smoothstep(0.12,0.40,z);
tree*=(1.0-0.30*uSoft)*(1.0-0.14*gu*uWind)*smoothstep(0.8,2.5,1.0/(tf*px));
float crest=smoothCrest-tree;
float soft=px*(mix(2.4,1.1,sat(z*2.0))+3.0*smoothstep(0.80,1.0,z))*(1.0+1.5*uSoft);
float a=sat((p.y-crest)/soft+0.5);
float lit=sat(0.12+0.88*pow(max(1.0-z,0.0001),1.2)*(0.40+0.60*lightProx)+0.30*lightCol*(1.0-z));
lit=mix(lit,0.80,dayAmt*0.7);
vec3 fogC=mix(fogShade,fogLit,lit);
if(i>0&&a<1.0){
float lvl=lvl0+(0.020+0.050*z)*g1(X*0.7+fi*5.0,3.0)-0.02*uRain;
float dpt=(p.y-lvl)/H;
float hug=exp(-max(smoothCrest-p.y,0.0)/(0.30*H));
float drift=t*(0.008+0.020*z+0.006*h11(fi*3.7))+(0.010+0.030*uWind)*gu;
vec2 fq=vec2(mod((X-drift)*mix(6.5,4.2,z),400.0),dpt*1.3+mod(t*0.04,400.0)+fi*5.0);
float n1=fbmp(fq);
float n2=vnp(vec2(fq.x*3.0+mod(t*0.03,400.0),log(1.0+max(dpt,0.0)*3.0)*6.0+fi));
float F=dpt*0.9+0.50+((n1-0.5)*2.50*(1.0-0.25*uSoft)+(n2-0.5)*(0.14+0.16*uWind+0.30*z))*(1.0-0.45*hug)+0.20*hug+0.30*smoothstep(0.50,0.15,hh.x);
float dens=smoothstep(0.30-0.14*z,0.80+0.34*z,F);
float wv=vnp(vec2(fq.x*2.0+dpt*0.8+n1*1.6+11.0,dpt*0.9+mod(t*0.10,400.0)))*vnp(vec2(fq.x-dpt*0.6+n1*1.1+5.0,dpt*0.45+mod(t*0.045,400.0)+3.0));
dens=max(dens,smoothstep(0.16,0.56,wv*(0.50+n1))*smoothstep(-1.5,-0.2,dpt)*(0.50-0.22*z)*(0.55+0.75*smoothstep(0.60,0.20,hh.x))*(1.0-0.3*uSoft));
dens*=smoothstep(fogTop,fogTop+0.5*H,p.y);
float topLit=1.0-smoothstep(0.45,1.50,F);
float puff=sat(topLit*0.75+(n1-0.42)*1.5);
vec3 fc=mix(mix(fogShade,fogLit,lit*0.60)*0.93,mix(fogShade,fogLit,sat(lit*0.55+0.55)),puff)*(0.94+0.12*n2);
fc+=sunCol*(0.22*lightProx+0.09*uLevel)*puff*lightAmt*(1.0-z);
col=mix(col,fc,dens*mix(0.96,0.76,z*z));
}
if(a>0.0){
vec3 rc=mix(mix(rFar,rMid,smoothstep(0.0,0.55,z)),rNear,smoothstep(0.40,1.0,z));
rc=mix(rc,fogC,0.10*uSoft);
float washLen=0.050+0.07*z;
float below=max(p.y-smoothCrest,0.0);
float washK=0.50-0.22*z;
vec3 body=mix(rc,fogC,0.13+washK*(1.0-exp(-below/washLen)));
vec3 bodyFront=mix(rc,fogC,washK*(1.0-exp(-max(p.y-foldCrest,0.0)/washLen)));
body=mix(body,bodyFront,sat((p.y-foldCrest+tree)/(soft*1.5)+0.5));
float tex=vnp(vec2(X*tf*0.55,below*tf*0.9+fi*3.0));
body*=1.0+(tex-0.5)*0.14*tk*exp(-below/0.10)*calm;
float rim=exp(-max(p.y-crest,0.0)/(px*5.0+0.004*(1.0-z)))*lightProx*lightProx*(1.0-z)*lightAmt;
col=mix(col,body+sunCol*rim*0.24,a);
}
}
if(i<5){
float hz=sq(1.0-z*1.4)*smoothstep(0.40,0.56,p.y);
col=scr(col,sunCol*((0.12*exp(-ldw*3.4)+0.10*exp(-ld*12.0))*lightAmt+rays*calm*0.28)*hz*breath);
}
}
col=scr(col,sunCol*shafts*calm*0.144*breath);
if(p.y>0.50){
float Xp=cx+uFlow*0.62;
float cw=max(0.86*asp,1.15);
float pu=(Xp+0.43*asp)/cw;
float pid=floor(pu+0.5);
float lx=(pu-pid)*cw;
float ps=mod(pid,97.0);
vec2 nq=vec2(lx+mod(pid,7.0)*1.31,p.y);
float needle=fbm3(nq*64.0);
float lump=vn(nq*21.0+5.0);
float r0=h21(vec2(ps*3.0,7.7+floor(seedOff)));
float pa=0.0;
for(int j=0;j<2;j++){
float r=h21(vec2(ps*3.0+float(j),7.7+floor(seedOff)));
if((r-0.5)*(r0-0.5)<0.0)r=1.0-r;
float r2=fract(r*53.3);
float ox=j==0?(r2-0.5)*0.05:(fract(r*29.1)>0.5?1.0:-1.0)*(0.165+0.06*r2);
float H=j==0?0.38+0.09*r2:0.21+0.08*r2;
float qx=lx-ox;
if(abs(qx)<0.72*H&&(j==0||r2>0.10)){
float gt=gustp((cx-qx)*1.2,t);
float sway=(0.010+0.030*uWind)*(0.6*sin(t*0.83+r*40.0)+0.4*sin(t*1.37+r*17.0))*(0.45+0.9*gt)+0.030*uWind*gt;
float d=pine(vec2(qx,1.03-p.y)/H,r,needle,lump,sway,t)*H;
pa=max(pa,smoothstep(-0.0045,0.0045,d/(1.0+0.6*uSoft)));
}
}
col=mix(col,rNear*0.74,pa*(0.97-0.12*uSoft));
}
col=mix(col,vec3(dot(col,vec3(0.299,0.587,0.114)))*vec3(0.94,0.97,1.03),0.30*uRain);
return finish(col,xy);
}
uniform float uDim;
uniform vec4 uVeil;
uniform float uFade;
void main() {
    vec4 c = sceneMain(vec2(gl_FragCoord.x, uSize.y - gl_FragCoord.y));
    gl_FragColor = vec4(mix(c.rgb, uVeil.rgb, uVeil.a) * (1.0 - uDim), uFade);
}
