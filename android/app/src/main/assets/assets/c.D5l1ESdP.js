import{j as i,m as d}from"./e.CTYcQwqj.js";import{B as r}from"./c.cXnkxpvy.js";import{M as t}from"./c.Clmg6Sgb.js";import"./c.CuDEm0KI.js";import"./c.BW-XyaGS.js";import"./c.ChxAWldX.js";import"./c.DI_5yP3v.js";import"./c.BhE4TZsF.js";import"./c.BRWYH1ou.js";import"./c.FZjG-SCy.js";import"./c.BzSG06Ep.js";import"./c.HTeLTF9G.js";import"./c.D2ozk-_G.js";import"./c.B8uU1cMT.js";import"./c.p018AHG0.js";const s=({title:e,code:n})=>i.jsxs("div",{className:"ds-code-block p-5 rounded-lg text-sm",dir:"ltr",children:[i.jsx("div",{className:"text-emerald-700 dark:text-emerald-300 font-semibold",children:e}),i.jsx("pre",{className:"mt-3 whitespace-pre-wrap leading-6",children:n})]}),l=()=>i.jsxs("div",{className:"rounded-2xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 p-5 shadow-sm",children:[i.jsx("h4",{className:"font-bold text-center text-slate-800 dark:text-slate-100 mb-4",children:"חמש דרכי סוגריים עבור ארבע מטריצות"}),i.jsx("div",{className:"grid grid-cols-1 md:grid-cols-5 gap-3",dir:"ltr",children:["(A1(A2(A3A4)))","(A1((A2A3)A4))","((A1A2)(A3A4))","((A1(A2A3))A4)","(((A1A2)A3)A4)"].map(e=>i.jsx("div",{className:"rounded-xl border border-violet-200 dark:border-violet-800 bg-violet-50 dark:bg-violet-900/20 p-3 text-center font-mono text-sm",children:e},e))})]}),N=()=>i.jsxs(d.div,{initial:{opacity:0,y:12},animate:{opacity:1,y:0},transition:{duration:.35},className:"space-y-6",children:[i.jsx("h1",{className:"text-3xl font-bold text-slate-900 dark:text-white",children:"כפל שרשרת מטריצות"}),i.jsxs(r,{type:"problem",title:"הבעיה",children:[i.jsxs("p",{children:["נתונות מטריצות ",i.jsx(t,{children:String.raw`A_1A_2\cdots A_n`}),", ואפשר לכפול אותן בסדרי סוגריים שונים. התוצאה האלגברית זהה, אבל מספר הכפלות הסקלריות עשוי להשתנות מאוד."]}),i.jsx(t,{block:!0,children:String.raw`A_i\text{ has dimensions }p_{i-1}\times p_i`}),i.jsxs("p",{children:["כפל מטריצה בגודל ",i.jsx(t,{children:String.raw`a\times b`})," במטריצה בגודל",i.jsx(t,{children:String.raw`b\times c`})," עולה ",i.jsx(t,{children:String.raw`a\cdot b\cdot c`})," פעולות כפל סקלריות."]})]}),i.jsx(l,{}),i.jsxs(r,{type:"example",title:"אותו כפל, מחיר שונה",children:[i.jsxs("p",{children:["עבור ",i.jsx(t,{children:String.raw`A_{10\times100}`}),", ",i.jsx(t,{children:String.raw`B_{100\times5}`}),", ",i.jsx(t,{children:String.raw`C_{5\times50}`}),":"]}),i.jsx(t,{block:!0,children:String.raw`((AB)C):\quad 10\cdot100\cdot5+10\cdot5\cdot50=7500`}),i.jsx(t,{block:!0,children:String.raw`(A(BC)):\quad 100\cdot5\cdot50+10\cdot100\cdot50=75000`}),i.jsx("p",{children:"ההבדל הוא פי 10, ולכן בדיקה “לפי תחושה” אינה מספיקה."})]}),i.jsxs(r,{type:"definition",title:"תת-בעיה ונוסחת מעבר",children:[i.jsxs("p",{children:["נגדיר ",i.jsx(t,{children:String.raw`m[i,j]`})," כעלות המינימלית לכפול את השרשרת",i.jsx(t,{children:String.raw`A_iA_{i+1}\cdots A_j`}),"."]}),i.jsx(t,{block:!0,children:String.raw`
m[i,j]=
\begin{cases}
0 & i=j\\
\min_{i\le k<j}\{m[i,k]+m[k+1,j]+p_{i-1}p_kp_j\} & i<j
\end{cases}`}),i.jsxs("p",{children:["הפיצול ",i.jsx(t,{children:"k"})," אומר שהכפל האחרון הוא בין",i.jsx(t,{children:String.raw`A_i\cdots A_k`})," לבין ",i.jsx(t,{children:String.raw`A_{k+1}\cdots A_j`}),"."]})]}),i.jsx(r,{type:"theorem",title:"מבנה אופטימלי",children:i.jsxs("p",{children:["אם הפיצול האופטימלי של ",i.jsx(t,{children:String.raw`A_i\cdots A_j`})," הוא אחרי",i.jsx(t,{children:"A_k"}),", אז גם הפתרון לשמאל וגם הפתרון לימין חייבים להיות אופטימליים. אחרת אפשר היה להחליף צד לא אופטימלי בצד טוב יותר ולשפר את הפתרון הכולל."]})}),i.jsxs(r,{type:"algorithm",title:"MATRIX-CHAIN-ORDER",children:[i.jsx(s,{title:"Bottom-up matrix-chain multiplication",code:`MATRIX-CHAIN-ORDER(p)
    let m[1..n, 1..n] and s[1..n-1, 2..n] be new tables

    for i = 1 to n
        m[i, i] = 0

    for l = 2 to n
        for i = 1 to n - l + 1
            j = i + l - 1
            m[i, j] = ∞
            for k = i to j - 1
                q = m[i, k] + m[k + 1, j] + p[i - 1]p[k]p[j]
                if q < m[i, j]
                    m[i, j] = q
                    s[i, j] = k

    return m and s`}),i.jsxs("p",{className:"mt-4",children:["יש ",i.jsx(t,{children:String.raw`\Theta(n^2)`})," תתי-בעיות, ולכל אחת מנסים עד",i.jsx(t,{children:"n"})," פיצולים, לכן זמן הריצה הוא ",i.jsx(t,{children:String.raw`\Theta(n^3)`}),"והזיכרון הוא ",i.jsx(t,{children:String.raw`\Theta(n^2)`}),"."]})]}),i.jsxs(r,{type:"algorithm",title:"שחזור הסוגריים האופטימליים",children:[i.jsx(s,{title:"PRINT-OPTIMAL-PARENS",code:`PRINT-OPTIMAL-PARENS(s, i, j)
    if i == j
        print "A_i"
    else
        print "("
        PRINT-OPTIMAL-PARENS(s, i, s[i, j])
        PRINT-OPTIMAL-PARENS(s, s[i, j] + 1, j)
        print ")"`}),i.jsxs("p",{className:"mt-4",children:["הטבלה ",i.jsx(t,{children:String.raw`s[i,j]`})," שומרת את הפיצול האופטימלי, ולכן אפשר לשחזר את הסוגריים ברקורסיה."]})]})]});export{N as default};
