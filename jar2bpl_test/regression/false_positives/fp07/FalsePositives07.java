

import java.math.BigInteger;
import java.security.SecureRandom;



public class FalsePositives07 {

    private SecureRandom    init_random;

    private static final BigInteger ONE = BigInteger.valueOf(1);
    private static final BigInteger TWO = BigInteger.valueOf(2);

	
	public long procedure_Aa(long x0, long c, BigInteger[] pq, int size)
    {
        //Verify and perform condition: 0<x<2^32; 0<c<2^32; c - odd.
        while(x0<0 || x0>4294967296L)
        {
            x0 = init_random.nextInt()*2;
        }

        while((c<0 || c>4294967296L) || (c/2==0))
        {
            c = init_random.nextInt()*2+1;
        }

        BigInteger C = new BigInteger(Long.toString(c));
        BigInteger constA32 = new BigInteger("97781173");

        //step1
        BigInteger[] y = new BigInteger[1]; // begin length = 1
        y[0] = new BigInteger(Long.toString(x0));

        //step 2
        int[] t = new int[1]; // t - orders; begin length = 1
        t[0] = size;
        int s = 0;
        for (int i=0; t[i]>=33; i++)
        {
            // extension array t
            int tmp_t[] = new int[t.length + 1];             ///////////////
            System.arraycopy(t,0,tmp_t,0,t.length);          //  extension
            t = new int[tmp_t.length];                       //  array t
            System.arraycopy(tmp_t, 0, t, 0, tmp_t.length);  ///////////////

            t[i+1] = t[i]/2;
            s = i+1;
        }

        //step3
        BigInteger p[] = new BigInteger[s+1];
        p[s] = new BigInteger("8000000B",16); //set min prime number length 32 bit

        int m = s-1;  //step4

        for (int i=0; i<s; i++)
        {
            int rm = t[m]/32;  //step5

     step6: for(;;)
            {
                //step 6
                BigInteger tmp_y[] = new BigInteger[y.length];  ////////////////
                System.arraycopy(y,0,tmp_y,0,y.length);         //  extension
                y = new BigInteger[rm+1];                       //  array y
                System.arraycopy(tmp_y,0,y,0,tmp_y.length);     ////////////////

                for (int j=0; j<rm; j++)
                {
                    y[j+1] = (y[j].multiply(constA32).add(C)).mod(TWO.pow(32));
                }

                //step 7
                BigInteger Ym = new BigInteger("0");
                for (int j=0; j<rm; j++)
                {
                    Ym = Ym.add(y[j].multiply(TWO.pow(32*j)));
                }

                y[0] = y[rm]; //step 8

                //step 9
                BigInteger N = TWO.pow(t[m]-1).divide(p[m+1]).
                                   add((TWO.pow(t[m]-1).multiply(Ym)).
                                       divide(p[m+1].multiply(TWO.pow(32*rm))));

                if (N.mod(TWO).compareTo(ONE)==0) 
                {
                    N = N.add(ONE);
                }

                int k = 0; //step 10

        step11: for(;;)
                {
                    //step 11
                    p[m] = p[m+1].multiply(N.add(BigInteger.valueOf(k))).add(ONE);

                    if (p[m].compareTo(TWO.pow(t[m]))==1)
                    {
                        continue step6; //step 12
                    }

                    //step13
                    if ((TWO.modPow(p[m+1].multiply(N.add(BigInteger.valueOf(k))),p[m]).compareTo(ONE)==0) &&
                        (TWO.modPow(N.add(BigInteger.valueOf(k)),p[m]).compareTo(ONE)!=0))
                    {
                        m -= 1;
                        break;
                    }
                    else
                    {
                        k += 2;
                        continue step11;
                    }
                }

                if (m>=0)
                {
                    break; //step 14
                }
                else
                {
                    pq[0] = p[0];
                    pq[1] = p[1];
                    return y[0].longValue(); //return for procedure B' step 2
                }
            }
        }
        return y[0].longValue();
    }
	
	   //Procedure B'
    private void procedure_Bb(long x0, long c, BigInteger[] pq)
    {
        //Verify and perform condition: 0<x<2^32; 0<c<2^32; c - odd.
        while(x0<0 || x0>4294967296L)
        {
            x0 = init_random.nextInt()*2;
        }

        while((c<0 || c>4294967296L) || (c/2==0))
        {
            c = init_random.nextInt()*2+1;
        }

        BigInteger [] qp = new BigInteger[2];
        BigInteger q = null, Q = null, p = null;
        BigInteger C = new BigInteger(Long.toString(c));
        BigInteger constA32 = new BigInteger("97781173");

        //step1
        x0 = procedure_Aa(x0, c, qp, 256);
        q = qp[0];

        //step2
        x0 = procedure_Aa(x0, c, qp, 512);
        Q = qp[0];

        BigInteger[] y = new BigInteger[33];
        y[0] = new BigInteger(Long.toString(x0));

        int tp = 1024;

 step3: for(;;)
        {
            //step 3
            for (int j=0; j<32; j++)
            {
                y[j+1] = (y[j].multiply(constA32).add(C)).mod(TWO.pow(32));
            }

            //step 4
            BigInteger Y = new BigInteger("0");
            for (int j=0; j<32; j++)
            {
                Y = Y.add(y[j].multiply(TWO.pow(32*j)));
            }

            y[0] = y[32]; //step 5

            //step 6
            BigInteger N = TWO.pow(tp-1).divide(q.multiply(Q)).
                               add((TWO.pow(tp-1).multiply(Y)).
                                   divide(q.multiply(Q).multiply(TWO.pow(1024))));

            if (N.mod(TWO).compareTo(ONE)==0)
            {
                N = N.add(ONE);
            }

            int k = 0; //step 7

     step8: for(;;)
            {
                //step 11
                p = q.multiply(Q).multiply(N.add(BigInteger.valueOf(k))).add(ONE);

                if (p.compareTo(TWO.pow(tp))==1)
                {
                    continue step3; //step 9
                }

                //step10
                if ((TWO.modPow(q.multiply(Q).multiply(N.add(BigInteger.valueOf(k))),p).compareTo(ONE)==0) &&
                    (TWO.modPow(q.multiply(N.add(BigInteger.valueOf(k))),p).compareTo(ONE)!=0))
                {
                    pq[0] = p;
                    pq[1] = q;
                    return;
                }
                else
                {
                    k += 2;
                    continue step8;
                }
            }
        }
    }
	
	
}
