import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: '/auth/:path*',
        destination: 'http://127.0.0.1:5000/auth/:path*'
      },
      {
        source: '/ceremony/:path*',
        destination: 'http://127.0.0.1:5000/ceremony/:path*'
      },
      {
        source: '/keys/:path*',
        destination: 'http://127.0.0.1:5000/keys/:path*'
      },
      {
        source: '/profiles/:path*',
        destination: 'http://127.0.0.1:5000/profiles/:path*'
      },
      {
        source: '/serial/:path*',
        destination: 'http://127.0.0.1:5000/serial/:path*'
      },
      {
        source: '/storage/:path*',
        destination: 'http://127.0.0.1:5000/storage/:path*'
      },
      {
        source: '/users/:path*',
        destination: 'http://127.0.0.1:5000/users/:path*'
      },
      {
        source: '/api/:path*',
        destination: 'http://127.0.0.1:5000/api/:path*'
      }
    ];
  }
};

export default nextConfig;
