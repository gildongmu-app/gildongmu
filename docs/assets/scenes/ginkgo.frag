// ginkgo — tools/site/agsl2glsl.mjs 가 app/src/main/assets/scenes/_lib.agsl + ginkgo.agsl 에서 만든 파일. 손으로 고치지 말 것.
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
const float GROUND=0.885;
const float TAU=6.2831853;
float vnp(vec2 p){
vec2 i=floor(p);
vec2 f=p-i;
vec2 u=f*f*(3.0-2.0*f);
vec2 i0=mod(i,256.0);
vec2 i1=mod(i+1.0,256.0);
return mix(mix(h21(i0),h21(vec2(i1.x,i0.y)),u.x),mix(h21(vec2(i0.x,i1.y)),h21(i1),u.x),u.y);
}
float fbp3(vec2 p){
return(0.5*vnp(p)+0.25*vnp(p*2.0+vec2(13.7,7.1))+0.125*vnp(p*4.0+vec2(31.1,17.3)))/0.875;
}
float fbp(vec2 p){
return fbp3(p)*0.875+0.0625*vnp(p*8.0+vec2(5.3,41.9))+0.03125*vnp(p*16.0+vec2(71.3,23.7));
}
float smax(float a,float b,float k){
float h=max(k-abs(a-b),0.0)/k;
return max(a,b)+h*h*k*0.25;
}
vec3 ginkgo(vec2 q,float cleft,float ph){
vec2 e=q+vec2(0.0,0.24);
float rr=length(e);
float th=atan(e.x,e.y+1e-6);
float R=0.66*(1.0+0.030*cos(th*9.0+ph));
float thm=mix(0.60,0.98,smoothstep(0.0,0.42,rr));
float d=smax(rr-R,rr*sin(clamp(abs(th)-thm,-1.57,1.57)),0.06);
vec2 w=vec2(abs(e.x),e.y-0.66*(1.0-cleft));
float dw=(w.x*0.14+w.y*0.990<0.0)?length(w):(w.x*0.990-w.y*0.14);
d=smax(d,-dw,0.035);
float sy=clamp(e.y,-0.50,0.04);
float ds=length(vec2(e.x-0.30*sy*sy,e.y-sy))-mix(0.024,0.015,sat(-sy/0.3));
return vec3(min(d,ds),th,rr);
}
float bigGust(float t){
return smoothstep(0.52,0.97,0.5+0.5*sin(t*0.19+1.7*sin(t*0.061+mod(uSeed,61.0))));
}
vec4 leafLayer(vec2 p,float fi,float t,float px,vec3 cA,vec3 cB,vec3 cShade,vec3 haze,float amt,float lk){
float z=fi/5.0;
float S=0.070*pow(1.5,fi);
float v=0.030*pow(1.42,fi);
float blur=max(px*0.9,(0.9+0.55*abs(fi-2.0)+2.6*sq(max(fi-3.0,0.0)))/1208.0);
float slant=0.20+0.75*uWind;
float ph=fi*1.7+mod(uSeed,61.0);
float wx=p.x*0.55+p.y*0.25;
float wamp=(0.45+1.3*uWind)*(0.55+0.75*z)*(1.0+0.7*bigGust(t-(uSize.x/uSize.y-p.x)*0.8));
vec2 w=p;
w.x+=wamp*(0.060*sin(0.47*t+wx*0.8+ph)+0.035*sin(0.83*t+wx*1.2+ph*2.3)+0.020*sin(1.31*t+wx*1.7+ph*0.7));
w.y+=wamp*(0.022*sin(0.61*t+wx*1.1+ph*1.3)+0.012*sin(1.07*t-wx*1.5+ph));
float gx=(w.x+slant*w.y+uFlow*(0.25+0.20*fi))/S+fi*7.31+mod(uSeed,61.0)*3.0;
float ix=floor(gx);
float sdx=(gx-ix)<0.5?-1.0:1.0;
vec4 acc=vec4(0.0);
for(int a=0;a<2;a++){
float cxi=ix+float(a)*sdx;
float cw=mod(cxi,61.0);
float hc=h11(cw+fi*13.0);
float vc=v*(0.78+0.44*hc);
float gy=(w.y-t*vc)/S+hc*19.0;
float iy=floor(gy);
float sdy=(gy-iy)<0.5?-1.0:1.0;
for(int b=0;b<2;b++){
float cyi=iy+float(b)*sdy;
vec2 id=vec2(cw,mod(cyi,53.0))+fi*17.0;
vec2 r1=h22(id);
vec2 r2=h22(id+5.3);
float trel=(19.0*hc-cyi-0.5)*S/vc;
float dens=mix(0.20,0.90,bigGust(trel-cw*S*0.7))*amt;
float ex=smoothstep(-0.04,0.04,dens-r2.y);
float len=S*(0.20+0.07*z)*(0.72+0.46*r2.x);
float sw=TAU*fract(t*(0.21+0.15*r1.y)/(1.0+0.7*z)+r2.y*3.0);
vec2 c=0.5+(r1-0.5)*0.50+vec2(0.17*sin(sw),0.05*cos(2.0*sw));
vec2 dl=(vec2(gx-cxi,gy-cyi)-c)*S;
dl.x-=slant*dl.y;
float rr=len*0.82+blur;
if(ex>0.0&&dot(dl,dl)<rr*rr){
float dir=r1.x<0.5?-1.0:1.0;
float ax=TAU*fract(r1.x*7.0+dir*t*0.03);
float ori=3.14159+(r1.y-0.5)*1.6+0.85*sin(sw+0.5)+TAU*fract(dir*t*0.017+r2.x);
float flip=TAU*fract(r1.y*5.0+t*(0.05+0.07*r1.x))+1.25*sin(sw);
float kf=cos(flip);
float kk=max(abs(kf),0.12);
vec2 a2=rot(ax)*dl;
a2.y-=0.28*sin(flip)*a2.x*a2.x/len;
vec2 b2=vec2(a2.x,a2.y/kk);
vec2 n=b2*inversesqrt(dot(b2,b2)+1e-9);
float gf=sqrt(n.x*n.x+n.y*n.y/(kk*kk));
vec2 q=rot(ori-ax)*b2/len;
vec3 g=ginkgo(q,0.10+0.34*r2.x*r2.x,r1.x*40.0);
float al=smoothstep(blur,-blur,g.x*len/gf);
vec3 pc=mix(cB,cA,smoothstep(0.04,0.55,g.z)*(0.75+0.25*r2.y));
pc*=0.96+0.04*cos(g.y*62.0)*smoothstep(0.12,0.30,g.z)*smoothstep(0.018,0.04,len);
float lit=0.5+0.5*cos(flip-0.7);
pc=mix(mix(cShade,pc,0.25),pc,lit);
pc+=cA*(0.16*pow(lit,10.0)+0.50*lk);
pc=mix(haze,pc,0.62+0.38*smoothstep(0.0,0.5,z));
pc=mix(pc,cA,0.45*smoothstep(0.6,1.0,z));
al*=ex*mix(0.80,1.0,kk)*mix(0.90,0.97,smoothstep(0.0,0.4,z))*(1.0-0.22*smoothstep(0.7,1.0,z));
acc.rgb=acc.rgb*(1.0-al)+pc*al;
acc.a=acc.a*(1.0-al)+al;
}
}
}
return acc;
}
vec4 ginkgoRow(vec2 q,float seed,vec3 lamp){
float i0=floor(q.x);
float H=0.0;
vec2 G=vec2(0.0);
float Wn=0.0;
float LL=0.0;
for(int k=0;k<3;k++){
float id=i0+float(k)-1.0;
float iw=mod(id,71.0);
float r=h11(iw+seed*3.0);
float r2=h11(iw*1.7+seed*5.0+3.3);
float size=0.74+0.40*r2;
float cx=id+0.5+(r-0.5)*0.36;
for(int j=0;j<6;j++){
float fj=float(j);
vec2 rj=h22(vec2(iw+fj*7.3,seed*11.0+fj));
float lead=step(fj,2.5);
float sgn=(fj<3.5?-1.0:1.0)*(r<0.5?1.0:-1.0);
float kq=step(4.5,fj);
vec2 off=mix(vec2(sgn*(0.28+0.18*kq),0.42+0.16*r+0.15*kq),vec2(0.0,0.55+0.20*fj),lead);
float rad0=mix(0.175-0.025*kq,0.195-0.033*fj,lead);
rad0*=1.0-step(1.5,fj)*lead*step(r2,0.40)*0.6;
vec2 c=vec2(cx,0.0)+(off+(rj-0.5)*vec2(0.16,0.12))*size;
float rad=rad0*size*(0.80+0.36*fract(rj.x*7.3));
vec2 dv=(q-c)*vec2(0.5,1.0);
float e=exp(-dot(dv,dv)/(rad*rad));
H+=e;
G+=dv/rad*e*e;
Wn+=e*e;
vec2 dl=(lamp.xy-c)*vec2(0.5,1.0);
float dist=length(dl)+1e-3;
LL+=e*e*sq(sat(1.0-dist/lamp.z))*sat(0.40+0.60*dot(dv/rad,dl/dist));
}
}
return vec4(H,G/(Wn+0.02),LL/(Wn+0.02));
}
float trunks(vec2 q,float seed){
float i0=floor(q.x);
float trunk=0.0;
for(int k=0;k<3;k++){
float id=i0+float(k)-1.0;
float iw=mod(id,71.0);
float r=h11(iw+seed*3.0);
float size=0.74+0.40*h11(iw*1.7+seed*5.0+3.3);
float tx=q.x-(id+0.5+(r-0.5)*0.36)-(r-0.5)*0.05*q.y;
float sp=q.y-0.33*size;
float tw=(mix(0.046,0.016,sat(q.y/(0.9*size)))+0.020*exp(-q.y*30.0))*size;
float stem=smoothstep(tw+0.012,tw-0.012,abs(tx))*smoothstep(0.92,0.70,q.y/size);
float lim=min(abs(tx-1.20*sp),abs(tx+1.30*sp))*0.62;
float limb=smoothstep(tw*0.7+0.012,tw*0.7-0.012,lim)*step(0.0,sp)*smoothstep(0.40,0.30,sp/size);
trunk=max(trunk,max(stem,limb));
}
return trunk;
}
vec2 dabs(vec2 p){
vec2 i=floor(p);
vec2 h=h22(mod(i,97.0)+0.5);
float ra=0.22+0.20*h.x*h.x;
float a=sat(1.0-length(p-i-0.5-(h-0.5)*(1.0-2.0*ra))/ra);
vec2 p2=mat2(0.94,-0.72,0.72,0.94)*p+vec2(3.7,1.9);
vec2 i2=floor(p2);
vec2 h2=h22(mod(i2,97.0)+31.5);
float rb=0.22+0.20*h2.x*h2.x;
float a2=sat(1.0-length(p2-i2-0.5-(h2-0.5)*(1.0-2.0*rb))/rb);
return a>a2?vec2(a,h.y):vec2(a2,h2.y);
}
vec4 paintRow(vec2 q,float seed,float bl,vec3 Ldir,vec3 deep,vec3 shade,vec3 light,vec3 trunkCol,vec3 litC,float lk,vec3 lamp,float sw){
q.x+=sw*sq(max(q.y-0.25,0.0));
vec4 r=ginkgoRow(q,seed,lamp);
vec2 qs=q*vec2(0.5,1.0);
float nz=fbp3(qs*7.0+seed*3.0);
vec2 qw=qs+vec2(nz-0.5,vnp(qs*9.0+seed+5.0)-0.5)*0.045;
vec2 d1=dabs(qw*vec2(8.0,10.0)+seed*3.0);
vec2 d2=dabs(qw*vec2(19.0,24.0)+seed*7.0);
float Hn=r.x+(d1.x-0.34)*0.40+(d2.x-0.30)*0.24+(nz-0.5)*0.24;
float a=smoothstep(0.50-0.07*bl,0.50+0.07*bl,Hn);
vec3 N=normalize(vec3(r.yz*1.15,1.0));
float tex=((d1.x-0.34)*0.32+(d2.x-0.30)*0.26+(d1.y-0.5)*0.14)/bl*1.2;
float l=dot(N,Ldir)+tex+(nz-0.5)*0.20;
vec3 f=mix(deep,shade,smoothstep(0.20,0.60,l));
f=mix(f,light,smoothstep(0.60,0.95,l));
f=mix(f,light,0.32*(1.0-smoothstep(0.45,1.05,Hn)));
f*=mix(vec3(1.0),mix(vec3(1.0,0.90,0.70),vec3(0.98,1.04,1.02),vnp(vec2(q.x*0.9+seed*3.0,1.5))),day4f(1.0,1.0,1.0,0.15));
float lit=smoothstep(0.03,0.46,r.w)*lk;
f=mix(f,litC*(0.86+0.50*tex),lit*(0.40+0.60*smoothstep(-0.22,0.10,tex)));
float tr=trunks(q,seed)*0.90*step(0.0,q.y)*(1.0-a*0.92);
vec3 tc=mix(trunkCol*(0.80+0.45*nz),litC*0.42,0.45*lit);
return vec4(f*a*(1.0-tr)+tc*tr,a+tr*(1.0-a));
}
vec3 arcAt(vec2 o,float a0,float k,float s){
float a1=a0+k*s;
return vec3(o+vec2(sin(a1)-sin(a0),cos(a0)-cos(a1))/k,a1);
}
vec2 arcSN(vec2 p,vec2 o,float a0,float k,float L){
float sg=k<0.0?-1.0:1.0;
vec2 v=p-(o+vec2(-sin(a0),cos(a0))/k);
float ang=atan(v.y,v.x)+sg*1.5708-a0;
return vec2(L*0.5+(mod(ang-k*L*0.5+3.14159,TAU)-3.14159)/k,sg*(1.0/abs(k)-length(v)));
}
vec4 bough(vec2 p,vec2 o,float a0,float k,float L,float w0,float seed,float bl,float t,float fl,vec3 cA,vec3 cB,vec3 cBr){
vec2 sn=arcSN(p,o,a0,k,L);
float s=sn.x;
float n=sn.y;
if(abs(n)>0.050||s<-0.06||s>L+0.04)return vec4(0.0);
float wob=0.0022*sin(s*60.0+seed*9.0);
float dB=abs(n-wob)-w0*mix(1.0,0.30,sat(s/L));
dB=max(dB,max(-s-0.05,s-L));
float br=smoothstep(bl,-bl,dB)*0.88;
vec4 acc=vec4(cBr*br,br);
float a1=a0+k*s;
vec2 dn=vec2(sin(a1),cos(a1));
float cell=0.018;
float ci=floor(s/cell);
for(int i=0;i<3;i++){
float id=ci+float(i)-1.0;
for(int j=0;j<2;j++){
vec2 r=h22(vec2(id+40.0,seed*13.0+float(j)*7.0));
vec2 r2=h22(vec2(id+40.0,seed*29.0+float(j)*3.0));
float sb=(id+0.2+0.6*r.x)*cell;
float has=step(r2.x,0.92)*step(sb,L+0.004)*step(0.0,sb);
float ha=(r.y-0.5)*1.6+fl*sin(t*(2.0+1.8*r2.y)+r.x*40.0);
vec2 hd=rot(ha)*dn;
vec2 dv=vec2(s-sb,n-wob);
float ly=dot(dv,hd);
float lx=dv.x*hd.y-dv.y*hd.x;
float R=0.0100+0.0070*r2.y;
float st=R*0.70;
if(has>0.5&&ly>-0.003&&ly<st+R+bl+0.002&&abs(lx)<R+bl){
float turn=clamp(0.72+(0.10+fl)*sin(t*(1.1+1.3*r.y)+r2.x*30.0)-0.16*r.x,0.34,1.0);
vec2 e=vec2(lx/turn,ly-st);
float rr=length(e);
float th=atan(e.x,e.y+1e-6);
float dF=smax(rr-R*(1.0+0.04*cos(th*7.0+r.x*9.0)),rr*sin(clamp(abs(th)-1.0,-1.57,1.57)),R*0.12);
vec2 w=vec2(abs(e.x),e.y-R*(0.90-0.34*r2.x));
dF=max(dF,-(w.y<0.0?length(w):w.x*0.985-w.y*0.17));
float dS=max(abs(lx)-0.0009,max(-ly,ly-st-R*0.2));
float al=smoothstep(bl,-bl,min(dF*turn,dS))*(0.82+0.16*r.x);
vec3 fc=mix(cB,cA,smoothstep(0.1,0.9,rr/R)*(0.55+0.45*r2.y)+0.30*(1.0-turn));
fc*=0.96+0.04*cos(th*26.0);
acc.rgb=acc.rgb*(1.0-al)+fc*al;
acc.a=acc.a*(1.0-al)+al;
}
}
}
return acc;
}
vec4 sceneMain(vec2 xy){
vec2 uv=xy/uSize;
float asp=uSize.x/uSize.y;
vec2 p=vec2(uv.x*asp,uv.y);
float px=1.0/uSize.y;
float t=uTime;
float sv=mod(uSeed,61.0);
float calm=1.0-0.40*uSoft;
float yUp=GROUND-p.y;
float lampOn=day4f(0.20,0.0,0.70,1.0);
float sunAmt=day4f(0.75,1.0,0.95,0.0);
float y=sat(p.y/GROUND);
vec3 top=day4(hx(98.0,110.0,162.0),hx(46.0,98.0,188.0),hx(70.0,60.0,132.0),hx(8.0,12.0,36.0));
vec3 mid=day4(hx(172.0,150.0,156.0),hx(88.0,142.0,210.0),hx(170.0,102.0,140.0),hx(20.0,26.0,66.0));
vec3 low=day4(hx(255.0,220.0,150.0),hx(216.0,222.0,220.0),hx(255.0,176.0,96.0),hx(58.0,50.0,84.0));
vec3 col=mix(top,mid,smoothstep(0.0,0.62,y));
col=mix(col,low,smoothstep(0.56,1.0,y));
vec2 sunP=day4(vec3(asp+0.30,0.52,0.0),vec3(-0.20,-0.34,0.0),vec3(-0.34,0.58,0.0),vec3(-0.2,-0.3,0.0)).xy;
vec3 sunCol=day4(hx(255.0,226.0,170.0),hx(255.0,240.0,200.0),hx(255.0,184.0,100.0),hx(255.0,200.0,120.0));
vec2 sp=p-sunP;
float sdist=length(sp);
col+=sunCol*(0.26*exp(-sdist*2.6))*sunAmt;
float nt=day4f(0.04,0.0,0.08,1.0);
if(nt>0.02){
float st=stars(p+vec2(uFlow*0.004,0.0),24.0)+stars(p*1.7+3.3,32.0)*0.6;
col+=vec3(0.86,0.90,1.0)*st*smoothstep(0.55,0.05,p.y)*nt*0.6;
}
vec2 cp=vec2(p.x*0.80+t*0.006+uFlow*0.012+sv,p.y*3.6);
float cl=fbp(cp+vec2(0.0,fbp3(cp*0.6+7.0)*0.9));
float cm=smoothstep(0.44,0.76,cl)*smoothstep(0.80,0.42,p.y)*smoothstep(0.04,0.28,p.y);
vec3 cCol=day4(hx(226.0,204.0,180.0),hx(170.0,198.0,232.0),hx(244.0,150.0,130.0),hx(40.0,44.0,92.0));
col=mix(col,cCol,cm*day4f(0.40,0.40,0.50,0.45));
vec3 hazeCol=day4(hx(252.0,226.0,172.0),hx(240.0,226.0,196.0),hx(255.0,184.0,110.0),hx(120.0,96.0,84.0));
float hz=exp(-max(yUp,0.0)*9.0);
col=mix(col,hazeCol,sat(hz*day4f(0.66,0.40,0.44,0.10+0.10*lampOn)*(1.0+0.40*uLevel)));
float sang=atan(sp.x,sp.y);
float st1=vnp(vec2(sang*9.0+sv,t*0.020));
float st2=vnp(vec2(sang*23.0+3.0,t*0.031+7.0));
float shaft=(smoothstep(0.40,0.78,st1)*(0.60+0.40*st2)+0.30*smoothstep(0.55,0.90,st2))*smoothstep(2.1,0.7,sdist)*smoothstep(0.35,0.95,sdist)*sunAmt;
float shK=min(shaft,0.85)*mix(0.40,1.0,smoothstep(0.25,0.70,p.y));
float lx=(p.x+uFlow*0.20)/0.57+0.36+sv*0.13;
vec2 lv=vec2((fract(lx)-0.5)*0.57,yUp-0.118);
float lampD=length(lv);
float lampG=sq(sat(1.0-lampD/0.28))*lampOn;
vec3 lampCol=hx(255.0,196.0,104.0);
vec3 litC=day4(hx(255.0,214.0,120.0),hx(255.0,206.0,84.0),hx(255.0,172.0,66.0),hx(255.0,206.0,84.0));
vec3 Ldir=normalize(day4(vec3(0.55,0.40,0.72),vec3(-0.45,0.62,0.64),vec3(-0.80,0.20,0.56),vec3(-0.38,0.56,0.74)));
vec3 deep=day4(hx(160.0,128.0,84.0),hx(150.0,106.0,16.0),hx(140.0,74.0,52.0),hx(22.0,26.0,52.0));
vec3 shade=day4(hx(220.0,180.0,98.0),hx(234.0,166.0,24.0),hx(220.0,126.0,50.0),hx(38.0,40.0,66.0));
vec3 light=day4(hx(255.0,228.0,132.0),hx(255.0,228.0,88.0),hx(255.0,186.0,76.0),hx(64.0,64.0,84.0));
vec3 trunkCol=day4(hx(104.0,92.0,96.0),hx(72.0,54.0,46.0),hx(54.0,30.0,46.0),hx(7.0,8.0,22.0));
float gw=(gust(p.x+uFlow*0.20,t)-0.30)*(0.25+uWind)*0.13;
if(yUp>-0.004){
float hxx=p.x+uFlow*0.05;
float wood=0.060+0.075*fbp3(vec2(hxx*2.2+3.0,1.0))+0.016*fbp3(vec2(hxx*9.0,4.0));
vec3 woodA=day4(hx(196.0,180.0,150.0),hx(150.0,150.0,130.0),hx(150.0,92.0,104.0),hx(18.0,22.0,50.0));
vec3 woodB=day4(hx(226.0,204.0,150.0),hx(214.0,176.0,96.0),hx(214.0,130.0,80.0),hx(26.0,28.0,56.0));
vec3 wc=mix(woodA,woodB,smoothstep(0.35,0.70,fbp3(vec2(hxx*6.0,yUp*16.0+2.0))));
col=mix(col,mix(wc,hazeCol,0.42),smoothstep(-0.010,0.010,wood-yUp)*0.90);
vec2 q1=vec2((p.x+uFlow*0.11)/0.095+3.7,(yUp-0.012)/0.185);
if(q1.y<1.4){
vec4 r1=paintRow(q1,1.0,1.8,Ldir,deep,shade,light,trunkCol,litC,0.40*lampOn,vec3(q1-lv/vec2(0.095,0.185),2.0),gw*0.6);
r1.rgb=mix(r1.rgb,hazeCol*r1.a,day4f(0.52,0.30,0.30,0.16));
col=col*(1.0-r1.a)+r1.rgb;
}
float mist=(0.55+0.45*fbp3(vec2(p.x*1.8+t*0.010+uFlow*0.14,2.0)))*exp(-max(yUp,0.0)*10.0);
col=mix(col,hazeCol,mist*day4f(0.72,0.26,0.30,0.12));
vec2 q2=vec2((p.x+uFlow*0.20)/0.150+1.3,yUp/0.300);
if(q2.y<1.4){
vec4 r2=paintRow(q2,2.0,1.3,Ldir,deep,shade,light,trunkCol,litC,day4f(0.6,0.0,0.62,1.0),vec3(q2-lv/vec2(0.150,0.300),1.40),gw);
col=col*(1.0-r2.a)+r2.rgb;
}
float post=smoothstep(px*2.4,px*1.0,abs(lv.x))*step(0.0,yUp)*step(yUp,0.118)*day4f(0.5,0.0,0.9,1.0);
col=mix(col,trunkCol*0.8,post*0.85);
col+=lampCol*(glow(lampD,px*4.5)*1.3+glow(lampD,0.034)*0.42+lampG*0.10)*lampOn*(1.0+0.16*uLevel);
}
if(yUp<0.004){
float gy=max(-yUp,0.0)/(1.0-GROUND);
float dep=1.0/(0.30+gy);
vec2 gp=vec2((p.x-asp*0.5)*dep+uFlow*0.66,dep*0.16);
float dap=vnp(vec2(gp.x*1.2+gp.y*8.0,gp.y*9.0)+3.0);
float spk=fbp3(gp*11.0);
float fleck=smoothstep(0.60,0.72,vnp(gp*vec2(46.0,60.0)+7.0))*smoothstep(0.35,0.60,vnp(gp*vec2(19.0,23.0)+2.0))*smoothstep(0.05,0.40,gy);
float lhx=vnp(gp*vec2(9.0,11.0)+4.0);
vec3 gLit=day4(hx(250.0,222.0,144.0),hx(252.0,208.0,72.0),hx(244.0,160.0,70.0),hx(34.0,36.0,58.0));
vec3 gShade=day4(hx(184.0,158.0,116.0),hx(166.0,116.0,30.0),hx(144.0,78.0,66.0),hx(13.0,16.0,38.0));
vec3 g=mix(gShade,gLit,sat(smoothstep(0.30,0.70,dap)*0.72+(spk-0.5)*0.70+0.16-0.30*exp(-gy*5.0)));
g=mix(g,mix(gShade,gLit,0.25+1.0*lhx),fleck*0.60);
float lpool=sq(sat(1.0-length(vec2(lv.x,gy*0.20))/0.27))*lampOn;
g=mix(g,litC*(0.50+0.55*spk+0.25*fleck),sat(lpool*1.1));
g=mix(g,hazeCol,day4f(0.55,0.32,0.35,0.10)*exp(-gy*6.0));
g*=1.0-0.22*gy*gy;
col=mix(col,g,smoothstep(0.004,-0.004,yUp));
}
vec3 shaftCol=day4(hx(255.0,232.0,180.0),hx(255.0,236.0,170.0),hx(255.0,180.0,96.0),hx(255.0,220.0,150.0));
col+=shaftCol*shK*day4f(0.20,0.15,0.17,0.0)*(1.0+0.35*uLevel);
vec2 dg=(p+vec2(uFlow*0.5+t*0.006,t*0.004))/0.040;
vec2 di=floor(dg);
vec2 dh=h22(vec2(mod(di.x,97.0),mod(di.y,89.0))+1.7);
vec2 dc=0.5+(dh-0.5)*0.7+0.12*vec2(sin(t*0.31+dh.x*30.0),cos(t*0.23+dh.y*30.0));
float dd=length(dg-di-dc)*0.040;
float tw=0.5+0.5*sin(t*(0.7+1.3*dh.y)+dh.x*50.0);
col+=shaftCol*glow(dd,max(px*0.6,(1.2+1.6*dh.x)/1208.0))*tw*tw*(shK*1.5+lampG*0.9)*calm*min(1.0,uSize.y/700.0);
vec3 cA=day4(hx(255.0,230.0,136.0),hx(255.0,216.0,62.0),hx(255.0,180.0,66.0),hx(246.0,196.0,84.0));
vec3 cB=day4(hx(240.0,204.0,108.0),hx(236.0,176.0,30.0),hx(232.0,126.0,40.0),hx(206.0,140.0,50.0));
vec3 cShade=day4(hx(208.0,176.0,110.0),hx(184.0,120.0,24.0),hx(160.0,76.0,56.0),hx(90.0,62.0,50.0));
float nightDim=day4f(0.0,0.0,0.15,0.88)*(1.0-sat(lampG*2.2+0.25*smoothstep(0.5,0.95,p.y)));
vec3 dimCol=day4(hx(120.0,110.0,110.0),hx(120.0,110.0,110.0),hx(90.0,60.0,80.0),hx(34.0,36.0,58.0));
cA=mix(cA,dimCol*1.25,nightDim);
cB=mix(cB,dimCol,nightDim);
cShade=mix(cShade,dimCol*0.7,nightDim);
float lk=shK*0.9+lampG*0.7;
float topFade=mix(0.80,1.0,smoothstep(0.30,0.62,p.y));
float qz=smoothstep(0.45,0.53,uv.x)*smoothstep(0.03,0.09,uv.y)*smoothstep(0.61,0.53,uv.y);
float cz=smoothstep(0.36,0.30,uv.x)*smoothstep(0.09,0.15,uv.y)*smoothstep(0.45,0.39,uv.y);
for(int i=0;i<4;i++){
float fi=float(i);
vec4 pl=leafLayer(p,fi,t,px,cA,cB,cShade,mix(col,hazeCol,0.65),(1.0-0.38*uSoft)*(1.0-0.38*qz),lk);
float k=(1.0-0.12*uSoft)*topFade*(i==0?1.0-0.5*uSoft:1.0)*(1.0-(i==3?0.88:0.52)*qz);
col=col*(1.0-pl.a*k)+pl.rgb*k;
}
float side=step(asp*0.5,p.x);
float sc=clamp(asp/1.39,0.55,1.15);
vec2 pm=vec2(mix(p.x,asp-p.x,side),p.y)/sc;
if(pm.x<0.40&&pm.y<0.40){
vec3 brCol=day4(hx(98.0,92.0,100.0),hx(64.0,50.0,48.0),hx(52.0,30.0,50.0),hx(6.0,7.0,20.0));
vec3 fA=day4(hx(240.0,214.0,130.0),hx(250.0,206.0,64.0),hx(255.0,176.0,70.0),hx(214.0,162.0,66.0));
vec3 fB=day4(hx(196.0,170.0,104.0),hx(214.0,152.0,30.0),hx(210.0,108.0,44.0),hx(104.0,76.0,44.0));
float sd0=1.0+side*3.0+sv*0.37;
float gx0=side*asp;
float gs=gust(asp-gx0,t)+0.6*bigGust(t-(asp-gx0)*0.8);
float dirS=1.0-2.0*side;
float sway=dirS*(0.030*sin(t*0.83+sd0)+0.08*(gs-0.35)+0.020*sin(t*1.9+sd0*2.0)*gs)*(0.5+uWind);
float fl=(0.05+0.20*gs)*(0.4+uWind);
float fx=pm.x*sc/asp;
float fy=pm.y*sc;
float keep=mix(1.0,smoothstep(1.0,0.40,length(vec2(fx/0.100,fy/0.062))),side);
float fade=smoothstep(0.5*asp/sc,0.5*asp/sc-0.08,pm.x)*keep*calm;
vec2 oC=vec2(-0.03,0.02+0.015*side);
float aC=0.50-0.08*side+sway*0.6;
float kC=1.1+sway*2.0;
float LC=0.34-0.07*side;
vec2 snC=arcSN(pm,oC,aC,kC,LC);
float dC=max(abs(snC.y)-0.0060*(1.0-0.7*sat(snC.x/LC)),snC.x-LC);
col=mix(col,brCol,smoothstep(0.012,-0.008,dC)*0.42*fade*(1.0-0.8*cz));
vec3 bkA=day4(hx(240.0,206.0,120.0),hx(244.0,184.0,44.0),hx(250.0,156.0,60.0),hx(200.0,148.0,66.0));
vec3 bkB=day4(hx(214.0,176.0,100.0),hx(220.0,148.0,30.0),hx(222.0,112.0,46.0),hx(112.0,80.0,48.0));
vec4 bk=vec4(0.0);
for(int i=0;i<12;i++){
float fi=float(i);
vec2 h=h22(vec2(fi*3.7+sd0,sd0*1.3+2.0));
vec2 h2=h22(vec2(fi*5.1+sd0+7.0,4.0));
float f=(fi-5.5+0.6*h.x)/6.0;
vec3 pt=arcAt(oC,aC,kC,LC*max(0.06+0.98*f,0.02));
vec2 c=i<6?vec2(0.01+0.20*h.x*h.x,0.17*h.y*h.y):pt.xy+(h.y-0.5)*0.090*vec2(-sin(pt.z),cos(pt.z));
c+=vec2(-0.5,0.25)*sway*dirS*(0.6+h2.x)+0.007*vec2(sin(t*0.5+fi*2.0),cos(t*0.4+fi));
float rB=i<6?0.040+0.034*h2.x:(0.028+0.026*h2.x)*(1.15-0.40*f);
vec2 dvB=pm-c+vec2(0.0,rB*0.6);
float dB=length(dvB);
float thB=atan(dvB.x,dvB.y+1e-6)+(h.x-0.5)*1.5+sway*3.0;
float blB=rB*(0.14+0.22*h2.x);
float aB=smoothstep(blB,-blB,max(dB-rB*1.5,dB*sin(clamp(abs(thB)-1.0,-1.57,1.57))))*(0.44+0.30*h2.y);
vec3 cB2=mix(bkB,bkA,h2.y*0.7+0.40*smoothstep(0.2,1.3,dB/rB));
bk=vec4(bk.rgb*(1.0-aB)+cB2*aB,bk.a*(1.0-aB)+aB);
}
float capB=mix(0.66-0.10*side,0.86,smoothstep(0.125,0.07,pm.x)*smoothstep(0.20,0.12,pm.y));
col=mix(col,bk.rgb/max(bk.a,1e-3)*(1.0+0.4*shK),capB*(1.0-exp(-1.4*bk.a/capB))*fade*(1.0-0.65*cz));
if(pm.x<0.135&&pm.y<0.215){
float bl=px*1.5/sc;
vec2 oA=vec2(0.012,-0.03);
float aA=1.36-sway*0.5;
float kA=0.95-sway*1.5;
vec4 bg=bough(pm,oA,aA,kA,0.172,0.0042,sd0,bl,t,fl,fA,fB,brCol);
vec3 j1=arcAt(oA,aA,kA,0.098);
vec4 b1=bough(pm,j1.xy,j1.z-1.42-sway*1.2,1.0,0.085,0.0022,sd0+1.0,bl,t,fl,fA,fB,brCol);
bg=bg*(1.0-b1.a)+b1;
vec3 j2=arcAt(oA,aA,kA,0.130);
vec4 b2=bough(pm,j2.xy,j2.z-0.62-sway*1.5,1.4,0.040,0.0020,sd0+2.0,bl,t,fl,fA,fB,brCol);
bg=bg*(1.0-b2.a)+b2;
float box=smoothstep(0.125,0.112,pm.x)*smoothstep(0.200,0.186,pm.y)*fade;
col=col*(1.0-bg.a*box)+bg.rgb*box*(1.0+0.4*shK);
}
}
for(int i=4;i<6;i++){
float fi=float(i);
vec4 pl=leafLayer(p,fi,t,px,cA,cB,cShade,col,0.7-0.3*uSoft,lk);
float k=(1.0-0.15*uSoft)*mix(0.82,1.0,smoothstep(0.25,0.60,p.y))*(1.0-qz)*(1.0-0.55*cz);
col=col*(1.0-pl.a*k)+pl.rgb*k;
}
return finish(col,xy);
}
uniform float uDim;
uniform vec4 uVeil;
uniform float uFade;
void main() {
    vec4 c = sceneMain(vec2(gl_FragCoord.x, uSize.y - gl_FragCoord.y));
    gl_FragColor = vec4(mix(c.rgb, uVeil.rgb, uVeil.a) * (1.0 - uDim), uFade);
}
